package net.lecousin.reactive.data.relational.query;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

import net.lecousin.reactive.data.relational.LcReactiveDataRelationalClient;
import net.lecousin.reactive.data.relational.mapping.LcMappingR2dbcConverter;
import net.lecousin.reactive.data.relational.model.ModelUtils;
import net.lecousin.reactive.data.relational.query.criteria.Criteria;
import reactor.core.publisher.Flux;

public class SelectQuery<T> {
	
	static class TableReference {
		
		TableReference source;
		String propertyName;
		Class<?> targetType;
		String alias;
		
		private TableReference(TableReference source, String propertyName, Class<?> targetType, String alias) {
			this.source = source;
			this.propertyName = propertyName;
			this.targetType = targetType;
			this.alias = alias;
		}

	}

	TableReference from;
	List<TableReference> joins = new LinkedList<>();
	Map<String, TableReference> tableAliases = new HashMap<>();
	Criteria where = null;
	long offset = 0;
	long limit = -1;
	
	private SelectQuery(Class<T> type, String alias) {
		if (alias == null)
			alias = generateAliasFor(type);
		from = new TableReference(null, null, type, alias);
		tableAliases.put(alias, from);
	}
	
	public static <T> SelectQuery<T> from(Class<T> type) {
		return from(type, null);
	}
	
	public static <T> SelectQuery<T> from(Class<T> type, String alias) {
		return new SelectQuery<>(type, alias);
	}

	public SelectQuery<T> join(String entityName, String propertyName) {
		return join(entityName, propertyName, null);
	}
	
	public SelectQuery<T> join(String entityName, String propertyName, String alias) {
		TableReference source = tableAliases.get(entityName);
		TableReference table = new TableReference(source, propertyName, null, alias != null ? alias : generateTableAlias());
		joins.add(table);
		tableAliases.put(table.alias, table);
		return this;
	}
	
	public SelectQuery<T> where(Criteria criteria) {
		if (where == null)
			where = criteria;
		else
			where = where.and(criteria);
		return this;
	}
	
	public SelectQuery<T> limit(long start, long nb) {
		this.offset = start;
		this.limit = nb;
		return this;
	}
	
	public Flux<T> execute(LcReactiveDataRelationalClient client) {
		return client.execute(this);
	}
	
	
	private String generateAliasFor(Class<?> type) {
		String alias = type.getSimpleName();
		if (!tableAliases.containsKey(alias))
			return alias;
		int i;
		for (i = 2; tableAliases.containsKey(alias + i); ++i);
		return alias + i;
	}
	
	private String generateTableAlias() {
		int i;
		for (i = 1; tableAliases.containsKey("e" + i); ++i);
		return "e" + i;
	}
	
	void setJoinsTargetType(LcMappingR2dbcConverter mapper) {
		for (TableReference join : joins) {
			if (join.targetType == null) {
				RelationalPersistentEntity<?> joinSourceEntity = mapper.getMappingContext().getRequiredPersistentEntity(join.source.targetType);
				RelationalPersistentProperty property = joinSourceEntity.getPersistentProperty(join.propertyName);
				if (property != null) {
					join.targetType = property.getActualType();
				} else {
					Field f = ModelUtils.getRequiredForeignTableFieldForProperty(join.source.targetType, join.propertyName);
					if (ModelUtils.isCollection(f))
						join.targetType = ModelUtils.getCollectionType(f);
					else
						join.targetType = f.getType();
				}
			}
		}
	}
	
}
