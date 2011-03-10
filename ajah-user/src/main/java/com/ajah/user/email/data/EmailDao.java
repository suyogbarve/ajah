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
package com.ajah.user.email.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ajah.user.UserId;
import com.ajah.user.email.Email;
import com.ajah.user.email.EmailId;
import com.ajah.user.email.EmailImpl;
import com.ajah.user.email.EmailStatusImpl;

/**
 * Data operations on the "user" table.
 * 
 * @author Eric F. Savage <code@efsavage.com>
 * 
 */
@Repository
public class EmailDao {

	private static final Logger log = Logger.getLogger(EmailDao.class.getName());

	private JdbcTemplate jdbcTemplate;

	/**
	 * Sets up a new JDBC template with the supplied data source.
	 * 
	 * @param dataSource
	 *            DataSource to use for a new JDBC template.
	 */
	@Autowired
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	/**
	 * Finds a email by the address field.
	 * 
	 * @param address
	 *            Value to match against email.address column.
	 * @return Email, if found, or null.
	 */
	public Email findEmailByAddress(String address) {
		try {
			return this.jdbcTemplate.queryForObject("SELECT email_id, address, status FROM email WHERE address = ?", new Object[] { address },
					new EmailRowMapper());
		} catch (EmptyResultDataAccessException e) {
			log.fine(e.getMessage());
			return null;
		}
	}

	static final class EmailRowMapper implements RowMapper<Email> {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Email mapRow(ResultSet rs, int rowNum) throws SQLException {
			Email user = new EmailImpl();
			user.setId(new EmailId(rs.getString("email_id")));
			user.setUserId(new UserId(rs.getString("user_id")));
			user.setAddress(rs.getString("address"));
			user.setStatus(EmailStatusImpl.get(rs.getString("status")));
			return user;
		}

	}

}