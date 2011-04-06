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
package com.ajah.user.data;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This is a basic DAO object. It's in the user library for now, but may move.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a
 *         href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * 
 */
public abstract class AbstractAjahDao {

	/**
	 * This method will return an Integer, functioning like getInt, but with the
	 * ability to recognize null values, instead of converting them to zero.
	 * 
	 * @see ResultSet#getInt(String)
	 * @see ResultSet#getObject(String)
	 * @param rs
	 *            The ResultSet to look in.
	 * @param field
	 *            The field name to look for.
	 * @return The Integer value of the field, may be null.
	 * @throws SQLException
	 *             If thrown by ResultSet.
	 */
	protected static Integer getInteger(ResultSet rs, String field) throws SQLException {
		if (rs.getObject(field) == null) {
			return null;
		}
		return Integer.valueOf(rs.getInt(field));
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
	protected static Long getLong(ResultSet rs, String field) throws SQLException {
		if (rs.getObject(field) == null) {
			return null;
		}
		return Long.valueOf(rs.getLong(field));
	}

}