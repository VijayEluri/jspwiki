/*
    JSPWiki - a JSP-based WikiWiki clone.

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.    
 */
package org.apache.wiki.auth.authorize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.wiki.TestEngine;
import org.apache.wiki.WikiSession;
import org.apache.wiki.auth.*;
import org.apache.wiki.auth.login.LdapLoginModule;
import org.freshcookies.security.Keychain;

/**
 * @author Andrew Jaquith
 */
public class LdapAuthorizerTest extends TestCase
{
    private TestEngine m_engine;

    protected void setUp() throws Exception
    {
        // Create the Keychain
        Keychain keychain = new Keychain();
        keychain.load( null, "keychain-password".toCharArray() );
        Keychain.Password password = new Keychain.Password( "password" );
        keychain.setEntry( LdapConfig.KEYCHAIN_BIND_DN_ENTRY, password );
        File file = new File("tests/etc/WEB-INF/test-keychain" );
        OutputStream stream = new FileOutputStream( file );
        keychain.store( stream, "keychain-password".toCharArray() );

        // Create the TestEngine properties
        Properties props = new Properties();
        props.load( TestEngine.findTestProperties() );

        // Set the LoginModule options
        props.put( UserManager.PROP_READ_ONLY_PROFILES, "true" );
        props.put( AuthenticationManager.PROP_LOGIN_MODULE, LdapLoginModule.class.getName() );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapLoginModule.OPTION_CONNECTION_URL, "ldap://127.0.0.1:4890" );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapLoginModule.OPTION_LOGIN_ID_PATTERN, "uid={0},ou=people,dc=jspwiki,dc=org" );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapLoginModule.OPTION_USER_BASE, "dc=jspwiki,dc=org" );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapLoginModule.OPTION_USER_PATTERN, "(&(objectClass=inetOrgPerson)(uid={0}))" );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapLoginModule.OPTION_AUTHENTICATION, "simple" );
        props.put( AuthenticationManager.PREFIX_LOGIN_MODULE_OPTIONS + LdapConfig.PROPERTY_SSL, "false" );

        // Set the Authorizer properties
        props.put( AuthorizationManager.PROP_AUTHORIZER, LdapAuthorizer.class.getCanonicalName() );
        props.put( LdapConfig.PROPERTY_ROLE_BASE, "ou=roles,dc=jspwiki,dc=org" );
        props.put( LdapConfig.PROPERTY_ROLE_PATTERN, "(&(objectClass=groupOfUniqueNames)(cn={0}))" );
        props.put( LdapConfig.PROPERTY_IS_IN_ROLE_PATTERN, "(&(&(objectClass=groupOfUniqueNames)(cn={0}))(uniqueMember={1}))" );
        props.put( LdapConfig.PROPERTY_BIND_DN, "uid=Fred,ou=people,dc=jspwiki,dc=org" );
        props.put( AuthenticationManager.PROP_KEYCHAIN_PATH, "test-keychain" );
        props.put( AuthenticationManager.PROP_KEYCHAIN_PASSWORD, "keychain-password" );

        m_engine = new TestEngine( props );
    }

    protected void tearDown() throws Exception
    {
        File file = new File("tests/etc/WEB-INF/test-keychain" );
        if ( file.exists() )
        {
            file.delete();
        }
    }

    public void testGetRoles() throws Exception
    {
        Authorizer authorizer = m_engine.getAuthorizationManager().getAuthorizer();

        // LDAP should return just 2 roles, Admin and Role1
        Principal[] roles = authorizer.getRoles();
        assertEquals( 2, roles.length );
        Role admin = new Role( "Admin" );
        Role role1 = new Role( "Role1" );
        assertTrue( roles[0].equals( admin ) || roles[1].equals( admin ) );
        assertTrue( roles[0].equals( role1 ) || roles[1].equals( role1 ) );
    }

    public void testFindRole() throws Exception
    {
        Authorizer authorizer = m_engine.getAuthorizationManager().getAuthorizer();
        
        // We should be able to find roles Admin and Role1
        assertEquals( new Role("Admin"), authorizer.findRole( "Admin" ) );
        assertEquals( new Role("Role1"), authorizer.findRole( "Role1" ) );
        
        // We should not be able to find role Authenticated
        assertNull( null, authorizer.findRole( "Authenticated" ) );
    }

    public void testIsUserInRole() throws Exception
    {
        assertTrue( m_engine.getUserManager().isReadOnly() );
        Authorizer authorizer = m_engine.getAuthorizationManager().getAuthorizer();
        Role admin = new Role( "Admin" );
        Role role1 = new Role( "Role1" );
        
        // Janne does not belong to any roles
        WikiSession session = m_engine.janneSession();
        assertFalse( authorizer.isUserInRole( session, admin ) );
        assertFalse( authorizer.isUserInRole( session, role1 ) );
        
        // The Admin belongs to just the Admin role
        session = m_engine.adminSession();
        assertTrue( authorizer.isUserInRole( session, admin ) );
        assertFalse( authorizer.isUserInRole( session, role1 ) );
    }

}