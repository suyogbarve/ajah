/*
 *  Copyright 2011 Eric F. Savage, code@efsavage.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.ajah.spring.jdbc;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.ManyToMany;
import javax.persistence.Transient;
import javax.sql.DataSource;

import lombok.extern.java.Log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.ajah.spring.jdbc.criteria.Criteria;
import com.ajah.spring.jdbc.criteria.Limit;
import com.ajah.spring.jdbc.criteria.Where;
import com.ajah.spring.jdbc.util.JDBCMapperUtils;
import com.ajah.util.AjahUtils;
import com.ajah.util.Identifiable;
import com.ajah.util.ToStringable;
import com.ajah.util.data.Audited;
import com.ajah.util.reflect.IntrospectionUtils;
import com.ajah.util.reflect.ReflectionUtils;

/**
 * This is a basic DAO object.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a
 *         href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * @param <K>
 *            The primary key class. Note that {@link Object#toString()} will be
 *            invoked on this object.
 * @param <T>
 *            The type of entity this DAO exists for, may be an interface.
 * @param <C>
 *            The concrete type of entity this DAO exists for.
 * 
 */
@Log
public abstract class AbstractAjahDao<K extends Comparable<K>, T extends Identifiable<K>, C extends T> implements AjahDao<K, T> {

	private static final Logger sqlLog = Logger.getLogger("ajah.sql");

	private static String getFieldsClause(final String[] fields) {
		final StringBuffer stringBuffer = new StringBuffer();
		boolean first = true;
		for (final String field : fields) {
			if (first) {
				first = false;
			} else {
				stringBuffer.append("AND ");
			}
			stringBuffer.append(field);
			stringBuffer.append("=? ");
		}
		return stringBuffer.toString();
	}

	/**
	 * This method will return a Long, functioning like getLong, but with the
	 * ability to recognize null values, instead of converting them to zero.
	 * 
	 * @see ResultSet#getLong(String)
	 * @see ResultSet#getObject(String)
	 * @param rs
	 *            The ResultSet to look in.
	 * @param field
	 *            The field name to look for.
	 * @return The Long value of the field, may be null.
	 * @throws SQLException
	 *             If thrown by ResultSet.
	 */
	protected static Long getLong(final ResultSet rs, final String field) throws SQLException {
		if (rs.getObject(field) == null) {
			return null;
		}
		return Long.valueOf(rs.getLong(field));
	}

	private static PropertyDescriptor getProp(final Field field, final PropertyDescriptor[] props) {
		for (final PropertyDescriptor prop : props) {
			if (prop.getName().equals(field.getName())) {
				return prop;
			}
		}
		return null;
	}

	private final Map<String, Field> colMap = new HashMap<>();

	private List<String> columns;

	protected JdbcTemplate jdbcTemplate;

	private String selectFields;

	private String selectFieldsWithTablePrefix;

	private String insertFields;

	private String updateFields;

	private List<String> updateFieldsList;

	private String insertPlaceholders;

	private String tableName;

	/**
	 * Will automatically fill in properties from the result set. Currently
	 * supports:
	 * 
	 * <ul>
	 * <li>{@link String}</li>
	 * <ul>
	 * 
	 * @throws SQLException
	 *             If the {@link ResultSet} throws it.
	 * 
	 * @see com.ajah.spring.jdbc.AjahDao#autoPopulate(Identifiable, ResultSet)
	 */
	@Override
	public void autoPopulate(final T entity, final ResultSet rs) throws SQLException {
		try {
			final BeanInfo componentBeanInfo = Introspector.getBeanInfo(entity.getClass());
			final PropertyDescriptor[] props = componentBeanInfo.getPropertyDescriptors();
			for (final PropertyDescriptor prop : props) {
				log.finest("PropertyDescriptor: " + prop.getName() + ", Setter: " + (prop.getWriteMethod() == null ? null : prop.getWriteMethod().getName()) + " Getter: "
						+ (prop.getReadMethod() == null ? null : prop.getReadMethod().getName()));
			}
			for (final String column : getColumns()) {
				final Field field = this.colMap.get(column);
				if (rs.getObject(column) == null) {
					propSet(entity, getProp(field, props), null);
				} else if (IntrospectionUtils.isString(field)) {
					propSet(entity, getProp(field, props), rs.getString(column));
				} else if (IntrospectionUtils.isDate(field)) {
					propSet(entity, getProp(field, props), new Date(rs.getLong(column)));
				} else if (IntrospectionUtils.isFromStringable(field)) {
					propSet(entity, getProp(field, props), field.getType().getConstructor(String.class).newInstance(rs.getString(column)));
				} else if (IntrospectionUtils.isIdentifiableEnum(field)) {
					propSet(entity, getProp(field, props), ReflectionUtils.findEnumById(field, rs.getString(column)));
				} else if (IntrospectionUtils.isInt(field)) {
					if (rs.getObject(column) == null && IntrospectionUtils.isPrimitive(field)) {
						log.warning("Attempting to set a null value on a primitive int field, using zero");
						propSet(entity, getProp(field, props), Integer.valueOf(0));
					} else {
						propSet(entity, getProp(field, props), Integer.valueOf(rs.getInt(column)));
					}
				} else if (IntrospectionUtils.isLong(field)) {
					if (rs.getObject(column) == null && IntrospectionUtils.isPrimitive(field)) {
						log.warning("Attempting to set a null value on a primitive long field, using zero");
						propSet(entity, getProp(field, props), Long.valueOf(0));
					} else {
						propSet(entity, getProp(field, props), Long.valueOf(rs.getLong(column)));
					}
				} else if (IntrospectionUtils.isBoolean(field)) {
					propSet(entity, getProp(field, props), Boolean.valueOf(rs.getBoolean(column)));
				} else if (IntrospectionUtils.isEnum(field)) {
					log.warning("Can't handle non-Identifiable enum for column " + column + " [" + field.getType() + "]");
				} else {
					log.warning("Can't handle auto-populating of column " + column + " of type " + field.getType());
				}
			}
		} catch (final IntrospectionException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		} catch (final SecurityException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		} catch (final InstantiationException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		} catch (final IllegalAccessException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		} catch (final InvocationTargetException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		} catch (final NoSuchMethodException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		}
	}

	protected long count(final Criteria criteria) {
		try {
			final String sql = "SELECT COUNT(*) FROM " + getTableName() + criteria.getWhere().getSql();
			sqlLog.finest(sql);
			return getJdbcTemplate().queryForInt(sql, criteria.getWhere().getValues().toArray());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	protected long count(final String sql) {
		try {
			sqlLog.finest(sql);
			return getJdbcTemplate().queryForInt(sql);
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	/**
	 * Decrements the field of the record by 1.
	 * 
	 * @param entity
	 *            Entity to update.
	 * @param field
	 *            The field to decrease.
	 * @return Number of rows affected.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	public int decrement(final T entity, final String field) throws DatabaseAccessException {
		return increment(entity, field, -1);
	}

	/**
	 * Note: As a safety mechanishm, this method throws
	 * {@link UnsupportedOperationException}. It should be overridden as needed.
	 * 
	 * @see com.ajah.spring.jdbc.AjahDao#delete(com.ajah.util.Identifiable)
	 * @see #deleteById(Comparable)
	 */
	@Override
	public int delete(final T entity) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Deletes an entity by unique ID.
	 * 
	 * @param id
	 *            Value to match against the entity.entity_id column, required.
	 * @return Entity if found, otherwise null.
	 * @throws DatabaseAccessException
	 *             If the query could not be executed.
	 */
	public int deleteById(final K id) throws DatabaseAccessException {
		AjahUtils.requireParam(id, "id");
		try {
			return getJdbcTemplate().update("DELETE FROM " + getTableName() + " WHERE " + getTableName() + "_id = ?", new Object[] { id.toString() });
		} catch (final DataAccessException e) {
			throw new DatabaseAccessException(e);
		}

	}

	/**
	 * Finds a single object by the Criteria specified.
	 * 
	 * @param criteria
	 *            The criteria to use to find the object.
	 * @return The object, if found.
	 */
	public T find(final Criteria criteria) {
		if (criteria.getLimit().getCount() > 1) {
			throw new IllegalArgumentException("Cannot use singular find method when criteria has a limit greater than 1 (" + criteria.getLimit().getCount() + ")");
		}
		criteria.rows(1);
		return find(criteria.getWhere(), criteria.getLimit());
	}

	/**
	 * Finds a single object by the Where and Limit specified.
	 * 
	 * @param where
	 *            The Object to create the WHERE statement.
	 * @param limit
	 *            The Object to create the LIMIT statement.
	 * @return The object, if found, otherwise null.
	 */
	public T find(final Where where, final Limit limit) {
		AjahUtils.requireParam(where, "where");
		if (limit != null && limit.getCount() > 1) {
			throw new IllegalArgumentException("Cannot use singular find method with a limit greater than 1 (" + limit.getCount() + ")");
		}
		try {
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + where.getSql() + (limit == null ? " LIMIT 1" : " " + limit.getSql());
			sqlLog.finest(sql);
			return getJdbcTemplate().queryForObject(sql, where.getValues().toArray(), getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			return null;
		}
	}

	/**
	 * Find an entity by unique ID.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required.
	 * @return Entity if found, otherwise null.
	 */
	public T findByField(final String field, final Object value) {
		AjahUtils.requireParam(field, "field");
		AjahUtils.requireParam(value, "value");
		try {
			return getJdbcTemplate().queryForObject("SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + field + " = ?", new Object[] { value }, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			return null;
		}
	}

	/**
	 * Find an entity by unique ID.
	 * 
	 * @param fields
	 *            Columns to match against, required.
	 * @param values
	 *            Values to match against the entity.field column, required.
	 * @return Entity if found, otherwise null.
	 */
	public T findByFields(final String[] fields, final Object[] values) {
		AjahUtils.requireParam(fields, "fields");
		AjahUtils.requireParam(values, "values");
		try {
			// TODO Optimize for single values
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + getFieldsClause(fields);
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
				for (final Object value : values) {
					log.finest(value.toString());
				}
			}
			return getJdbcTemplate().queryForObject(sql, values, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return null;
		}
	}

	/**
	 * Find a collections of entities by their unique ID.
	 * 
	 * @param ids
	 *            Values to match against the entity.entity_id column, required.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> findByIds(final Collection<K> ids) {
		AjahUtils.requireParam(ids, "ids");
		try {
			final StringBuffer sql = new StringBuffer();
			sql.append("SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE ");
			boolean first = true;
			final String[] idArray = new String[ids.size()];
			int i = 0;
			for (final K id : ids) {
				if (first) {
					first = false;
				} else {
					sql.append(" OR ");
				}
				sql.append(getTableName() + "_id = ?");
				idArray[i++] = id.toString();
			}
			if (log.isLoggable(Level.FINEST)) {
				log.finest(sql.toString());
				for (final Object value : ids) {
					log.finest(value.toString());
				}
			}
			return getJdbcTemplate().query(sql.toString(), idArray, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return null;
		}
	}

	/**
	 * Find an entity by the supplied WHERE clause.
	 * 
	 * @param where
	 *            The WHERE clause to include in the query.
	 * @return Entity if found, otherwise null.
	 */
	public T findByWhere(final String where) {
		AjahUtils.requireParam(where, "where");
		try {
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + where + " LIMIT 1";
			sqlLog.finest(sql);
			return getJdbcTemplate().queryForObject(sql, null, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			return null;
		}
	}

	/**
	 * Returns the list of columns for this class.
	 * 
	 * @return The list of columns for this class, may be empty but will not be
	 *         null.
	 */
	public List<String> getColumns() {
		if (this.columns == null) {
			loadColumns();
		}
		return this.columns;
	}

	private String getInsertFields() {
		if (this.insertFields == null) {
			loadColumns();
		}
		return this.insertFields;
	}

	private String getInsertPlaceholders() {
		if (this.insertPlaceholders == null) {
			loadColumns();
		}
		return this.insertPlaceholders;
	}

	private Object[] getInsertValues(final T entity) {
		final Object[] values = new Object[getColumns().size()];
		try {
			final BeanInfo componentBeanInfo = Introspector.getBeanInfo(entity.getClass());
			final PropertyDescriptor[] props = componentBeanInfo.getPropertyDescriptors();
			for (int i = 0; i < values.length; i++) {
				final Field field = this.colMap.get(this.columns.get(i));
				values[i] = ReflectionUtils.propGetSafeAuto(entity, field, getProp(field, props));
				if (log.isLoggable(Level.FINEST)) {
					log.finest(field.getName() + " set to " + values[i]);
				}
			}
		} catch (final IntrospectionException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		}
		return values;
	}

	/**
	 * Returns the Spring JDBC template.
	 * 
	 * @return The Spring JDBC template, may be null.
	 */
	public JdbcTemplate getJdbcTemplate() {
		return this.jdbcTemplate;
	}

	protected RowMapper<T> getRowMapper() {
		return new SimpleAjahRowMapper<>(this);
	}

	/**
	 * Returns the fields that are used when SELECTing an entity. Alias for
	 * {@link #getSelectFields(boolean)} with parameter value of false.
	 * 
	 * @return The columns for use in SELECT statements for this class, may be
	 *         empty but will not be null.
	 */
	public String getSelectFields() {
		return getSelectFields(false);
	}

	/**
	 * Returns the fields that are used when SELECTing an entity.
	 * 
	 * @param tablePrefix
	 *            Should we include the name of the table as a prefix for all
	 *            columns? Useful for complex queries.
	 * @return The columns for use in SELECT statements for this class, may be
	 *         empty but will not be null.
	 */
	public String getSelectFields(final boolean tablePrefix) {
		if (this.selectFields == null) {
			loadColumns();
		}
		return tablePrefix ? this.selectFieldsWithTablePrefix : this.selectFields;
	}

	/**
	 * Returns the table name for this class.
	 * 
	 * @return The table name for this class, may be null.
	 */
	public String getTableName() {
		if (this.tableName == null) {
			loadColumns();
		}
		return this.tableName;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Class<C> getTargetClass() {
		return (Class<C>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[2];
	}

	private String getUpdateFields() {
		if (this.updateFields == null) {
			loadColumns();
		}
		return this.updateFields;
	}

	private List<String> getUpdateFieldsList() {
		if (this.updateFieldsList == null) {
			loadColumns();
		}
		return this.updateFieldsList;
	}

	private Object[] getUpdateValues(final T entity) {
		final Object[] values = new Object[getUpdateFieldsList().size() + 1];
		try {
			final BeanInfo componentBeanInfo = Introspector.getBeanInfo(entity.getClass());
			final PropertyDescriptor[] props = componentBeanInfo.getPropertyDescriptors();
			for (int i = 0; i < (values.length - 1); i++) {
				final Field field = this.colMap.get(this.updateFieldsList.get(i));
				values[i] = ReflectionUtils.propGetSafeAuto(entity, field, getProp(field, props));
			}
			values[values.length - 1] = entity.getId().toString();
		} catch (final IntrospectionException e) {
			log.log(Level.SEVERE, entity.getClass().getName() + ": " + e.getMessage(), e);
		}
		return values;
	}

	/**
	 * Increments the field of the record by 1.
	 * 
	 * @param entity
	 *            Entity to update.
	 * @param field
	 *            The field to increase.
	 * @return Number of rows affected.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	public int increment(final T entity, final String field) throws DatabaseAccessException {
		return increment(entity, field, 1);
	}

	/**
	 * Increments the field of the record by a certain amount.
	 * 
	 * @param entity
	 *            Entity to update.
	 * @param field
	 *            The field to increase.
	 * @param amount
	 *            The amount to increase the field by.
	 * @return Number of rows affected.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	public int increment(final T entity, final String field, final int amount) throws DatabaseAccessException {
		AjahUtils.requireParam(entity, "entity");
		AjahUtils.requireParam(entity.getId(), "entity.id");
		AjahUtils.requireParam(this.jdbcTemplate, "this.jdbcTemplate");
		try {
			final String sql = "UPDATE " + getTableName() + " SET " + field + "=" + field + " + " + amount + " WHERE " + getTableName() + "_id = ?";
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
			}
			return this.jdbcTemplate.update(sql, entity.getId().toString());
		} catch (final DataAccessException e) {
			throw new DatabaseAccessException(e);
		}
	}

	/**
	 * Inserts the record. May throw an error on duplicate key exceptions.
	 * 
	 * @param entity
	 *            Entity to insert into the table.
	 * @return Number of rows inserted.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	@Override
	public int insert(final T entity) throws DatabaseAccessException {
		return insert(entity, false);
	}

	/**
	 * Inserts the record. May throw an error on duplicate key exceptions.
	 * 
	 * @param entity
	 *            Entity to insert into the table.
	 * @param delayed
	 *            Use a DELAYED insert.
	 * @return Number of rows inserted.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	public int insert(final T entity, final boolean delayed) throws DatabaseAccessException {
		AjahUtils.requireParam(entity, "entity");
		AjahUtils.requireParam(entity.getId(), "entity.id");
		AjahUtils.requireParam(this.jdbcTemplate, "this.jdbcTemplate");
		try {
			final String sql = "INSERT " + (delayed ? "DELAYED " : "") + "INTO " + getTableName() + "(" + getInsertFields() + ") VALUES (" + getInsertPlaceholders() + ")";
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
			}
			return this.jdbcTemplate.update(sql, getInsertValues(entity));
		} catch (final DataAccessException e) {
			throw new DatabaseAccessException(e);
		}
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param criteria
	 *            The criteria object to use to build the query.
	 * @return Entity if found, otherwise null.
	 * @since 1.0.1
	 */
	public List<T> list(final Criteria criteria) {
		AjahUtils.requireParam(criteria, "criteria");
		try {
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + criteria.getWhere().getSql() + criteria.getOrderBySql() + criteria.getLimit().getSql();
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
				log.finest(criteria.getWhere().getValues().toString());
			}
			return getJdbcTemplate().query(sql, criteria.getWhere().getValues().toArray(), getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Find a list of entities by an arbitrary WHERE clause.
	 * 
	 * @param where
	 *            The WHERE clause, required. Do not include the actual "WHERE"
	 *            phrase as it is inserted automatically.
	 * @return The list of entities satisfying the WHERE, may be null.
	 */
	public List<T> list(final String where) {
		AjahUtils.requireParam(where, "where");
		try {
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + where;
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
			}
			return getJdbcTemplate().query(sql, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> listByField(final String field, final String value) {
		AjahUtils.requireParam(value, "value");
		return listByField(field, value, getTableName() + "_id", 0, Integer.MAX_VALUE);
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required.
	 * @param count
	 *            The maximum number of rows to fetch.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> listByField(final String field, final String value, final int count) {
		AjahUtils.requireParam(value, "value");
		return listByField(field, value, getTableName() + "_id", 0, count);
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required. If
	 *            matching on "IS NULL", set this parameter to "NULL".
	 * @param orderBy
	 * @param page
	 *            Page of results (offset).
	 * @param count
	 *            Number of results per page to return.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> listByField(final String field, final String value, final String orderBy, final int page, final int count) {
		AjahUtils.requireParam(field, "field");
		AjahUtils.requireParam(value, "value");
		try {
			if (value.equals("NULL")) {
				final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + field + " IS NULL ORDER BY " + orderBy + " LIMIT " + (page * count) + "," + count;
				return getJdbcTemplate().query(sql, getRowMapper());
			}
			final String sql = "SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + field + " = ? ORDER BY " + orderBy + " LIMIT " + (page * count) + "," + count;
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
				log.finest(value.toString());
			}
			return getJdbcTemplate().query(sql, new Object[] { value }, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> listByField(final String field, final ToStringable value) {
		AjahUtils.requireParam(value, "value");
		return listByField(field, value.toString(), getTableName() + "_id", 0, Integer.MAX_VALUE);
	}

	/**
	 * Find a list of entities by non-unique match.
	 * 
	 * @param field
	 *            Column to match against, required.
	 * @param value
	 *            Value to match against the entity.field column, required.
	 * @param orderBy
	 * @param page
	 *            Page of results (offset).
	 * @param count
	 *            Number of results per page to return.
	 * @return Entity if found, otherwise null.
	 */
	public List<T> listByField(final String field, final ToStringable value, final String orderBy, final int page, final int count) {
		return listByField(field, value.toString(), orderBy, page, count);
	}

	/**
	 * Find an entity by unique ID.
	 * 
	 * @param id
	 *            Value to match against the entity.entity_id column, required.
	 * @return Entity if found, otherwise null.
	 * @throws DatabaseAccessException
	 *             If the query could not be executed.
	 */
	@Override
	public T load(final K id) throws DatabaseAccessException {
		AjahUtils.requireParam(id, "id");
		try {
			return getJdbcTemplate().queryForObject("SELECT " + getSelectFields() + " FROM " + getTableName() + " WHERE " + getTableName() + "_id = ?", new Object[] { id.toString() }, getRowMapper());
		} catch (final EmptyResultDataAccessException e) {
			log.finest(e.getMessage());
			return null;
		} catch (final DataAccessException e) {
			throw new DatabaseAccessException(e);
		}
	}

	private void loadColumns() {
		log.finest("Loading columns");
		if (this.tableName == null) {
			this.tableName = JDBCMapperUtils.getTableName(getTargetClass());
		}
		log.finest("Table set to : " + this.tableName);
		final List<String> columnList = new ArrayList<>();
		final List<String> newUpdateFields = new ArrayList<>();
		final StringBuffer select = new StringBuffer();
		final StringBuffer selectWithTablePrefix = new StringBuffer();
		log.finest(getTargetClass().getDeclaredFields().length + " declared fields for " + getTargetClass().getName());
		for (final Field field : getTargetClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(Transient.class)) {
				log.finest("Ignoring Transient field " + field.getName());
				continue;
			} else if (field.isAnnotationPresent(ManyToMany.class)) {
				log.finest("Ignoring ManyToMany field " + field.getName());
				continue;
			} else if (Collection.class.isAssignableFrom(field.getType())) {
				log.finest("Ignoring Collection field " + field.getName());
				continue;
			}
			final String colName = JDBCMapperUtils.getColumnName(getTableName(), field);
			columnList.add(colName);
			this.colMap.put(colName, field);
			if (select.length() > 0) {
				select.append(", ");
			}
			if (selectWithTablePrefix.length() > 0) {
				selectWithTablePrefix.append(", ");
			}

			select.append(colName);
			selectWithTablePrefix.append(this.tableName + "." + colName);
			if (!field.getName().equals("id")) {
				newUpdateFields.add(colName);
			}

			if (field.isAnnotationPresent(Audited.class)) {
				log.finest(field.getName() + " is audited");
				// TODO Audit it!
			}
		}
		if (this.selectFields == null) {
			this.selectFields = select.toString();
			this.selectFieldsWithTablePrefix = selectWithTablePrefix.toString();
			this.insertFields = this.selectFields;
		}
		if (this.columns == null) {
			this.columns = columnList;
		}
		log.finest(this.columns.size() + " columns");

		if (this.updateFields == null) {
			final StringBuffer uf = new StringBuffer();
			for (final String field : newUpdateFields) {
				if (uf.length() > 0) {
					uf.append(",");
				}
				uf.append(field);
				uf.append("=?");
			}
			this.updateFields = uf.toString();
			this.updateFieldsList = newUpdateFields;
		}

		final StringBuffer iph = new StringBuffer();
		for (int i = 0; i < this.columns.size(); i++) {
			if (i > 0) {
				iph.append(",");
			}
			iph.append("?");
		}
		this.insertPlaceholders = iph.toString();

	}

	protected int maxInt(final String field, final Criteria criteria) {
		try {
			final String sql = "SELECT MAX(" + field + ") FROM " + getTableName() + criteria.getWhere().getSql();
			return getJdbcTemplate().queryForInt(sql, criteria.getWhere().getValues().toArray());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	protected long maxLong(final String field, final Criteria criteria) {
		try {
			final String sql = "SELECT MAX(" + field + ") FROM " + getTableName() + criteria.getWhere().getSql();
			return getJdbcTemplate().queryForLong(sql, criteria.getWhere().getValues().toArray());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	protected int minInt(final String field, final Criteria criteria) {
		try {
			final String sql = "SELECT MIN(" + field + ") FROM " + getTableName() + criteria.getWhere().getSql();
			return getJdbcTemplate().queryForInt(sql, criteria.getWhere().getValues().toArray());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	protected long minLong(final String field, final Criteria criteria) {
		try {
			final String sql = "SELECT MIN(" + field + ") FROM " + getTableName() + criteria.getWhere().getSql();
			return getJdbcTemplate().queryForLong(sql, criteria.getWhere().getValues().toArray());
		} catch (final EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return 0;
		}
	}

	protected static int now() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	private void propSet(final T entity, final PropertyDescriptor prop, final Object value) {
		try {
			final Method setter = prop.getWriteMethod();
			if (setter == null) {
				throw new IllegalArgumentException("No setter found for " + prop.getName());
			}
			setter.invoke(entity, value);
		} catch (final IllegalAccessException e) {
			log.log(Level.SEVERE, prop.getName() + ": " + e.getMessage(), e);
		} catch (final IllegalArgumentException e) {
			// TODO See if we're trying to set a null on a primitive
			log.log(Level.SEVERE, prop.getName() + ": " + e.getMessage(), e);
		} catch (final InvocationTargetException e) {
			log.log(Level.SEVERE, prop.getName() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Sets up a new JDBC template with the supplied data source.
	 * 
	 * @param dataSource
	 *            DataSource to use for a new JDBC template.
	 */
	@Autowired
	public void setDataSource(final DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	/**
	 * Sets the table name. This will override any auto-discovered settings.
	 * 
	 * @param tableName
	 */
	public void setTableName(final String tableName) {
		this.tableName = tableName;
	}

	/**
	 * Updates the record. Will not do anything if there are no matching
	 * records.
	 * 
	 * @param entity
	 *            Entity to update.
	 * @return Number of rows affected.
	 * @throws DatabaseAccessException
	 *             If an error occurs executing the query.
	 */
	@Override
	public int update(final T entity) throws DatabaseAccessException {
		AjahUtils.requireParam(entity, "entity");
		AjahUtils.requireParam(entity.getId(), "entity.id");
		AjahUtils.requireParam(this.jdbcTemplate, "this.jdbcTemplate");
		try {
			final String sql = "UPDATE " + getTableName() + " SET " + getUpdateFields() + " WHERE " + getTableName() + "_id = ?";
			if (log.isLoggable(Level.FINEST)) {
				sqlLog.finest(sql);
			}
			return this.jdbcTemplate.update(sql, getUpdateValues(entity));
		} catch (final DataAccessException e) {
			throw new DatabaseAccessException(e);
		}
	}

}
