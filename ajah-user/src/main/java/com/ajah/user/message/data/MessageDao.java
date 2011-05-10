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
package com.ajah.user.message.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ajah.spring.jdbc.AbstractAjahDao;
import com.ajah.user.UserId;
import com.ajah.user.message.Message;
import com.ajah.user.message.MessageId;
import com.ajah.user.message.MessageImpl;
import com.ajah.user.message.MessageStatusImpl;
import com.ajah.user.message.MessageTypeImpl;
import com.ajah.util.AjahUtils;
import com.ajah.util.CollectionUtils;
import com.ajah.util.StringUtils;

/**
 * Data operations on the "message" table.
 * 
 * @author Eric F. Savage <code@efsavage.com>
 * 
 */
@Repository
public class MessageDao extends AbstractAjahDao<MessageId, Message> {

	private static final Logger log = Logger.getLogger(MessageDao.class.getName());

	private static final String SELECT_FIELDS = "message_id, created, sender, to, cc, bcc, subject, body, type, status";

	private static final String TABLE_NAME = "message";

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

	static final class MessageRowMapper implements RowMapper<Message> {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Message mapRow(ResultSet rs, int rowNum) throws SQLException {
			Message message = new MessageImpl(new MessageId(rs.getString("message_id")));
			message.setCreated(new Date(rs.getLong("created") * 1000));
			message.setSender(new UserId(rs.getString("sender")));
			message.setTo(getUserIds(rs.getString("to")));
			message.setCc(getUserIds(rs.getString("cc")));
			message.setBcc(getUserIds(rs.getString("bcc")));
			message.setSubject(rs.getString("subject"));
			message.setBody(rs.getString("body"));
			message.setType(MessageTypeImpl.get(rs.getString("type")));
			message.setStatus(MessageStatusImpl.get(rs.getString("status")));
			return message;
		}

		/**
		 * Converts a comma-separated list of User IDs into a type-safe list.
		 * 
		 * @param string
		 *            The list of IDs, separated by commas.
		 * @return A list of user ids, may be empty but will not be null.
		 */
		private List<UserId> getUserIds(String string) {
			List<UserId> userIds = new ArrayList<UserId>();
			if (StringUtils.isBlank(string)) {
				return userIds;
			}
			String[] stringIds = string.split(",");
			for (String stringId : stringIds) {
				userIds.add(new UserId(stringId));
			}
			return userIds;
		}
	}

	/**
	 * INSERTs a {@link Message} entity.
	 * 
	 * @param message
	 *            Message entity to insert, required.
	 */
	public void insert(Message message) {
		AjahUtils.requireParam(message, "message");
		AjahUtils.requireParam(this.jdbcTemplate, "this.jdbcTemplate");
		this.jdbcTemplate.update("INSERT INTO " + TABLE_NAME + " (" + SELECT_FIELDS + ") VALUES (?,?,?,?,?,?,?,?,?,?)",
				new Object[] { message.getId().getId(), toUnix(message.getCreated()), message.getSender().getId(), fromUserIds(message.getTo()),
						fromUserIds(message.getCc()), fromUserIds(message.getBcc()), message.getSubject(), message.getBody(),
						message.getType().getId(), message.getStatus().getId() });
		log.fine("Inserted message " + message.getId());
	}

	/**
	 * Converts a list of user IDs to a comma-delimted string.
	 * 
	 * @param userIds
	 *            The list of user IDs, may be null or empty.
	 * @return Comma-separated list of User IDs. If a null or empty list is
	 *         passed in, will return null.
	 */
	private String fromUserIds(List<UserId> userIds) {
		if (CollectionUtils.isEmpty(userIds)) {
			return null;
		}
		if (userIds.size() == 1) {
			return userIds.get(0).getId();
		}
		StringBuilder stringIds = new StringBuilder();
		for (int i = 0; i < userIds.size(); i++) {
			if (i > 0) {
				stringIds.append(",");
			}
			stringIds.append(userIds.get(i).getId());
		}
		return stringIds.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected JdbcTemplate getJdbcTemplate() {
		return this.jdbcTemplate;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getSelectFields() {
		return SELECT_FIELDS;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getTableName() {
		return TABLE_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RowMapper<Message> getRowMapper() {
		return new MessageRowMapper();
	}

}