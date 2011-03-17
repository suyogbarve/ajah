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
package com.ajah.user.login;

/**
 * LoginSource is where a login was from. They will generally correspond with
 * channels (web, mobile, phone) and/or specific applications.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * 
 */
public interface LogInSource {

	/**
	 * The internal ID of the source.
	 * 
	 * @return The internal ID of the source. Cannot be null.
	 */
	String getId();

	/**
	 * The short, display-friendly code of the source. If no code is applicable,
	 * it should be an alias for the ID.
	 * 
	 * @return The short, display-friendly code of the source. Cannot be null.
	 */
	String getCode();

	/**
	 * The display-friendly name of the source. If no name is applicable, it
	 * should be an alias for the ID or code.
	 * 
	 * @return The display-friendly name of the source. Cannot be null.
	 */
	String getName();

	/**
	 * The display-friendly description of the source.
	 * 
	 * @return The display-friendly description of the source. May be null.
	 */
	String getDescription();

}