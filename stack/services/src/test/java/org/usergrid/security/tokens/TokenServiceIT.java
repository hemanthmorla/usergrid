package org.usergrid.security.tokens;


import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.ServiceITSetup;
import org.usergrid.ServiceITSetupImpl;
import org.usergrid.ServiceITSuite;
import org.usergrid.cassandra.ClearShiroSubject;
import org.usergrid.cassandra.Concurrent;
import org.usergrid.management.ApplicationInfo;
import org.usergrid.management.OrganizationInfo;
import org.usergrid.management.OrganizationOwnerInfo;
import org.usergrid.management.UserInfo;
import org.usergrid.persistence.EntityManager;
import org.usergrid.persistence.entities.Application;
import org.usergrid.security.AuthPrincipalInfo;
import org.usergrid.security.AuthPrincipalType;
import org.usergrid.security.tokens.cassandra.TokenServiceImpl;
import org.usergrid.security.tokens.exceptions.ExpiredTokenException;
import org.usergrid.security.tokens.exceptions.InvalidTokenException;
import org.usergrid.utils.UUIDUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@Concurrent()
public class TokenServiceIT {

    static Logger log = LoggerFactory.getLogger( TokenServiceIT.class );

    // app-level data generated only once
    private static UserInfo adminUser;

    @Rule
    public ClearShiroSubject clearShiroSubject = new ClearShiroSubject();

    @ClassRule
    public static ServiceITSetup setup = new ServiceITSetupImpl( ServiceITSuite.cassandraResource );


    @BeforeClass
    public static void setup() throws Exception {
        log.info( "in setup" );
        adminUser =
                setup.getMgmtSvc().createAdminUser( "edanuff34", "Ed Anuff", "ed@anuff34.com", "test", false, false );
        OrganizationInfo organization = setup.getMgmtSvc().createOrganization( "TokenServiceTestOrg", adminUser, true );

        // TODO update to organizationName/applicationName
        setup.getMgmtSvc().createApplication( organization.getUuid(), "TokenServiceTestOrg/ed-application" ).getId();
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testEmailConfirmToken() throws Exception {

        Map<String, Object> data = new HashMap<String, Object>() {
            {
                put( "email", "ed@anuff34.com" );
                put( "username", "edanuff34" );
            }
        };


        String tokenStr = setup.getTokenSvc().createToken( TokenCategory.EMAIL, "email_confirm", null, data, 0 );

        log.info( "token: " + tokenStr );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( tokenStr );

        long last_access = tokenInfo.getAccessed();

        assertEquals( "email_confirm", tokenInfo.getType() );
        assertEquals( "ed@anuff34.com", tokenInfo.getState().get( "email" ) );
        assertEquals( "edanuff34", tokenInfo.getState().get( "username" ) );

        tokenInfo = setup.getTokenSvc().getTokenInfo( tokenStr );

        assertTrue( last_access < tokenInfo.getAccessed() );
    }


    @Test
    public void testAdminPrincipalToken() throws Exception {

        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.ADMIN_USER, adminUser.getUuid(), UUIDUtils.newTimeUUID() );

        String tokenStr = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );

        log.info( "token: " + tokenStr );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( tokenStr );

        long last_access = tokenInfo.getAccessed();

        assertEquals( "access", tokenInfo.getType() );
        assertEquals( adminUser.getUuid(), tokenInfo.getPrincipal().getUuid() );

        tokenInfo = setup.getTokenSvc().getTokenInfo( tokenStr );

        assertTrue( last_access < tokenInfo.getAccessed() );
    }


    @Test
    public void adminPrincipalTokenRevoke() throws Exception {


        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.ADMIN_USER, UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID() );

        String firstToken = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );
        String secondToken = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );

        assertNotNull( firstToken );
        assertNotNull( secondToken );

        TokenInfo firstInfo = setup.getTokenSvc().getTokenInfo( firstToken );
        assertNotNull( firstInfo );

        TokenInfo secondInfo = setup.getTokenSvc().getTokenInfo( secondToken );
        assertNotNull( secondInfo );

        setup.getTokenSvc().removeTokens( adminPrincipal );

        // tokens shouldn't be there anymore
        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( firstToken );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );

        invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( secondToken );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );
    }


    @Test
    public void userPrincipalTokenRevoke() throws Exception {
        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(),
                        UUIDUtils.newTimeUUID() );

        String firstToken = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );
        String secondToken = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );

        assertNotNull( firstToken );
        assertNotNull( secondToken );

        TokenInfo firstInfo = setup.getTokenSvc().getTokenInfo( firstToken );
        assertNotNull( firstInfo );

        TokenInfo secondInfo = setup.getTokenSvc().getTokenInfo( secondToken );
        assertNotNull( secondInfo );

        setup.getTokenSvc().removeTokens( adminPrincipal );

        // tokens shouldn't be there anymore
        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( firstToken );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );

        invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( secondToken );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );
    }


    @Test
    public void tokenDurationExpiration() throws Exception {
        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(),
                        UUIDUtils.newTimeUUID() );

        // 2 second token
        long expirationTime = 2000;

        String token =
                setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, expirationTime );

        long start = System.currentTimeMillis();

        assertNotNull( token );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( expirationTime, tokenInfo.getDuration() );
        long maxTokenAge = setup.getTokenSvc().getMaxTokenAge( token );
        assertEquals( expirationTime, maxTokenAge );


        tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( expirationTime, tokenInfo.getDuration() );

        maxTokenAge = setup.getTokenSvc().getMaxTokenAge( token );
        assertEquals( expirationTime, maxTokenAge );

        /**
         * Sleep at least expirationTime millis to allow token to expire
         */
        Thread.sleep( expirationTime - ( System.currentTimeMillis() - start ) + 1000 );

        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( token );
        }
        catch ( ExpiredTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );
    }


    @Test
    public void tokenDefaults() throws Exception {
        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(),
                        UUIDUtils.newTimeUUID() );

        long maxAge = ( ( TokenServiceImpl ) setup.getTokenSvc() ).getMaxPersistenceTokenAge();

        String token = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );

        assertNotNull( token );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( maxAge, tokenInfo.getDuration() );
    }


    @Test(expected = IllegalArgumentException.class)
    public void invalidDurationValue() throws Exception {

        long maxAge = ( ( TokenServiceImpl ) setup.getTokenSvc() ).getMaxPersistenceTokenAge();

        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(),
                        UUIDUtils.newTimeUUID() );

        setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, maxAge + 1 );
    }


    @Test
    public void appDefaultExpiration() throws Exception {

        OrganizationOwnerInfo orgInfo =
                setup.getMgmtSvc().createOwnerAndOrganization( "foo", "foobar", "foobar", "foo@bar.com", "foobar" );
        ApplicationInfo appInfo = setup.getMgmtSvc().createApplication( orgInfo.getOrganization().getUuid(), "bar" );
        EntityManager em = setup.getEmf().getEntityManager( appInfo.getId() );
        Application app = em.getApplication();
        AuthPrincipalInfo userPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(), app.getUuid() );
        String token = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, userPrincipal, null, 0 );
        assertNotNull( token );
        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( TokenServiceImpl.LONG_TOKEN_AGE, tokenInfo.getDuration() );
    }


    @Test
    public void appExpiration() throws Exception {

        OrganizationOwnerInfo orgInfo =
                setup.getMgmtSvc().createOwnerAndOrganization( "foo2", "foobar2", "foobar", "foo2@bar.com", "foobar" );

        ApplicationInfo appInfo = setup.getMgmtSvc().createApplication( orgInfo.getOrganization().getUuid(), "bar" );

        EntityManager em = setup.getEmf().getEntityManager( appInfo.getId() );

        Application app = em.getApplication();

        long appTokenAge = 1000;

        app.setAccesstokenttl( appTokenAge );

        em.updateApplication( app );

        AuthPrincipalInfo userPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(), app.getUuid() );

        String token = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, userPrincipal, null, 0 );

        long start = System.currentTimeMillis();

        assertNotNull( token );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( appTokenAge, tokenInfo.getDuration() );

        /**
         * Sleep at least expirationTime millis to allow token to expire
         */
        Thread.sleep( appTokenAge - ( System.currentTimeMillis() - start ) + 1000 );

        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( token );
        }
        catch ( ExpiredTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );
    }


    @Test
    public void tokenDeletion() throws Exception {
        AuthPrincipalInfo adminPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(),
                        UUIDUtils.newTimeUUID() );

        String realToken = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, adminPrincipal, null, 0 );

        assertNotNull( realToken );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( realToken );
        assertNotNull( tokenInfo );

        setup.getTokenSvc().revokeToken( realToken );

        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( realToken );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );

        String fakeToken = "notarealtoken";

        setup.getTokenSvc().revokeToken( fakeToken );
    }


    @Test
    public void appExpirationInfinite() throws Exception {

        OrganizationOwnerInfo orgInfo = setup.getMgmtSvc().createOwnerAndOrganization( "appExpirationInfinite",
                "appExpirationInfinite", "foobar", "appExpirationInfinite@bar.com", "foobar" );

        ApplicationInfo appInfo = setup.getMgmtSvc().createApplication( orgInfo.getOrganization().getUuid(), "bar" );

        EntityManager em = setup.getEmf().getEntityManager( appInfo.getId() );

        Application app = em.getApplication();

        long appTokenAge = 0;

        app.setAccesstokenttl( appTokenAge );

        em.updateApplication( app );

        AuthPrincipalInfo userPrincipal =
                new AuthPrincipalInfo( AuthPrincipalType.APPLICATION_USER, UUIDUtils.newTimeUUID(), app.getUuid() );

        String token = setup.getTokenSvc().createToken( TokenCategory.ACCESS, null, userPrincipal, null, 0 );


        assertNotNull( token );

        TokenInfo tokenInfo = setup.getTokenSvc().getTokenInfo( token );
        assertNotNull( tokenInfo );
        assertEquals( Long.MAX_VALUE, tokenInfo.getDuration() );

        boolean invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( token );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertFalse( invalidTokenException );

        setup.getTokenSvc().revokeToken( token );

        invalidTokenException = false;

        try {
            setup.getTokenSvc().getTokenInfo( token );
        }
        catch ( InvalidTokenException ite ) {
            invalidTokenException = true;
        }

        assertTrue( invalidTokenException );
    }
}
