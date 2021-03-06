/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb.factory;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.neo4j.graphdb.config.InvalidSettingException;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.helpers.AdvertisedSocketAddress;
import org.neo4j.helpers.ListenSocketAddress;
import org.neo4j.io.ByteUnit;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.HttpConnector;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.kernel.configuration.HttpConnector.Encryption.TLS;

public class GraphDatabaseSettingsTest
{
    @Test
    public void mustHaveNullDefaultPageCacheMemorySizeInBytes() throws Exception
    {
        Long bytes = Config.defaults().get( GraphDatabaseSettings.pagecache_memory );
        assertThat( bytes, is( nullValue() ) );
    }

    @Test
    public void pageCacheSettingMustAcceptArbitraryUserSpecifiedValue() throws Exception
    {
        Setting<Long> setting = GraphDatabaseSettings.pagecache_memory;
        assertThat( Config.defaults( setting, "245760" ).get( setting ),
                is( ByteUnit.kibiBytes( 240 ) ) );
        assertThat( Config.defaults( setting, "2244g" ).get( setting ),
                is( ByteUnit.gibiBytes( 2244 ) ) );
    }

    @Test( expected = InvalidSettingException.class )
    public void pageCacheSettingMustRejectOverlyConstrainedMemorySetting() throws Exception
    {
        long pageSize = Config.defaults().get( GraphDatabaseSettings.mapped_memory_page_size );
        Setting<Long> setting = GraphDatabaseSettings.pagecache_memory;
        // We configure the page cache to have one byte less than two pages worth of memory. This must throw:
        Config.defaults( setting, "" + ( pageSize * 2 - 1 ) ).get( setting );
    }

    @Test
    public void noDuplicateSettingsAreAllowed() throws Exception
    {
        final HashMap<String,String> fields = new HashMap<>();
        for ( Field field : GraphDatabaseSettings.class.getDeclaredFields() )
        {
            if ( field.getType() == Setting.class )
            {
                Setting setting = (Setting) field.get( null );

                assertFalse(
                        String.format( "'%s' in %s has already been defined in %s", setting.name(), field.getName(),
                                fields.get( setting.name() ) ), fields.containsKey( setting.name() ) );
                fields.put( setting.name(), field.getName() );
            }
        }
    }

    @Test
    public void groupToScopeSetting() throws Exception
    {
        // given
        String hostname = "my_other_host";
        int port = 9999;
        String scoping = "bla";
        Map<String,String> config = stringMap(
                GraphDatabaseSettings.default_advertised_address.name(), hostname,
                new BoltConnector( scoping ).advertised_address.name(), ":" + port
        );

        // when
        BoltConnector boltConnector = new BoltConnector( scoping );
        Setting<AdvertisedSocketAddress> advertised_address = boltConnector.advertised_address;
        AdvertisedSocketAddress advertisedSocketAddress = advertised_address.apply( config::get );

        // then
        assertEquals( hostname, advertisedSocketAddress.getHostname() );
        assertEquals( port, advertisedSocketAddress.getPort() );
    }

    @Test
    public void shouldEnableBoltByDefault() throws Exception
    {
        // given
        Config config = Config.builder().withServerDefaults().build();

        // when
        BoltConnector boltConnector = config.boltConnectors().get( 0 );
        ListenSocketAddress listenSocketAddress = config.get( boltConnector.listen_address );

        // then
        assertEquals( new ListenSocketAddress( "127.0.0.1", 7687 ), listenSocketAddress );
    }

    @Test
    public void shouldBeAbleToDisableBoltConnectorWithJustOneParameter() throws Exception
    {
        // given
        Config config = Config.defaults( new BoltConnector( "bolt" ).enabled, "false" );

        // then
        assertThat( config.boltConnectors().size(), is( 1 ) );
        assertThat( config.enabledBoltConnectors(), empty() );
    }

    @Test
    public void shouldBeAbleToOverrideBoltListenAddressesWithJustOneParameter() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap(
                "dbms.connector.bolt.enabled", "true",
                "dbms.connector.bolt.listen_address", ":8000" ) );

        BoltConnector boltConnector = config.boltConnectors().get( 0 );

        // then
        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ), config.get( boltConnector.listen_address ) );
    }

    @Test
    public void shouldDeriveBoltListenAddressFromDefaultListenAddress() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap(
                "dbms.connector.bolt.enabled", "true",
                "dbms.connectors.default_listen_address", "0.0.0.0" ) );

        BoltConnector boltConnector = config.boltConnectors().get( 0 );

        // then
        assertEquals( new ListenSocketAddress( "0.0.0.0", 7687 ), config.get( boltConnector.listen_address ) );
    }

    @Test
    public void shouldDeriveBoltListenAddressFromDefaultListenAddressAndSpecifiedPort() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap(
                "dbms.connectors.default_listen_address", "0.0.0.0",
                "dbms.connector.bolt.enabled", "true",
                "dbms.connector.bolt.listen_address", ":8000" ) );

        BoltConnector boltConnector = config.boltConnectors().get( 0 );

        // then
        assertEquals( new ListenSocketAddress( "0.0.0.0", 8000 ), config.get( boltConnector.listen_address ) );
    }

    @Test
    public void shouldStillSupportCustomNameForBoltConnector() throws Exception
    {
        Config config = Config.defaults( stringMap(
                "dbms.connector.random_name_that_will_be_unsupported.type", "BOLT",
                "dbms.connector.random_name_that_will_be_unsupported.enabled", "true",
                "dbms.connector.random_name_that_will_be_unsupported.listen_address", ":8000" ) );

        // when
        BoltConnector boltConnector = config.boltConnectors().get( 0 );

        // then
        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ), config.get( boltConnector.listen_address ) );
    }

    @Test
    public void shouldSupportMultipleBoltConnectorsWithCustomNames() throws Exception
    {
        Config config = Config.defaults( stringMap(
                "dbms.connector.bolt1.type", "BOLT",
                "dbms.connector.bolt1.enabled", "true",
                "dbms.connector.bolt1.listen_address", ":8000",
                "dbms.connector.bolt2.type", "BOLT",
                "dbms.connector.bolt2.enabled", "true",
                "dbms.connector.bolt2.listen_address", ":9000"
        ) );

        // when
        List<ListenSocketAddress> addresses = config.boltConnectors().stream()
                .map( c -> config.get( c.listen_address ) )
                .collect( Collectors.toList() );

        // then
        assertEquals( 2, addresses.size() );

        if ( addresses.get( 0 ).getPort() == 8000 )
        {
            assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ), addresses.get( 0 ) );
            assertEquals( new ListenSocketAddress( "127.0.0.1", 9000 ), addresses.get( 1 ) );
        }
        else
        {
            assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ), addresses.get( 1 ) );
            assertEquals( new ListenSocketAddress( "127.0.0.1", 9000 ), addresses.get( 0 ) );
        }
    }

    @Test
    public void shouldSupportMultipleBoltConnectorsWithDefaultAndCustomName() throws Exception
    {
        Config config = Config.defaults( stringMap(
                "dbms.connector.bolt.type", "BOLT",
                "dbms.connector.bolt.enabled", "true",
                "dbms.connector.bolt.listen_address", ":8000",
                "dbms.connector.bolt2.type", "BOLT",
                "dbms.connector.bolt2.enabled", "true",
                "dbms.connector.bolt2.listen_address", ":9000" ) );

        // when
        BoltConnector boltConnector1 = config.boltConnectors().get( 0 );
        BoltConnector boltConnector2 = config.boltConnectors().get( 1 );

        // then
        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ), config.get( boltConnector1.listen_address ) );
        assertEquals( new ListenSocketAddress( "127.0.0.1", 9000 ), config.get( boltConnector2.listen_address ) );
    }

    /// JONAS HTTP FOLLOWS
    @Test
    public void testServerDefaultSettings() throws Exception
    {
        // given
        Config config = Config.builder().withServerDefaults().build();

        // when
        List<HttpConnector> connectors = config.httpConnectors();

        // then
        assertEquals( 2, connectors.size() );
        if ( connectors.get( 0 ).encryptionLevel().equals( TLS ) )
        {
            assertEquals( new ListenSocketAddress( "localhost", 7474 ),
                    config.get( connectors.get( 1 ).listen_address ) );
            assertEquals( new ListenSocketAddress( "localhost", 7473 ),
                    config.get( connectors.get( 0 ).listen_address ) );
        }
        else
        {
            assertEquals( new ListenSocketAddress( "127.0.0.1", 7474 ),
                    config.get( connectors.get( 0 ).listen_address ) );
            assertEquals( new ListenSocketAddress( "127.0.0.1", 7473 ),
                    config.get( connectors.get( 1 ).listen_address ) );
        }
    }

    @Test
    public void shouldBeAbleToDisableHttpConnectorWithJustOneParameter() throws Exception
    {
        // given
        Config disableHttpConfig = Config.defaults(
                stringMap( "dbms.connector.http.enabled", "false",
                        "dbms.connector.https.enabled", "false" ) );

        // then
        assertTrue( disableHttpConfig.enabledHttpConnectors().isEmpty() );
        assertEquals( 2, disableHttpConfig.httpConnectors().size() );
    }

    @Test
    public void shouldBeAbleToOverrideHttpListenAddressWithJustOneParameter() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap(
                "dbms.connector.http.enabled", "true",
                "dbms.connector.http.listen_address", ":8000" ) );

        // then
        assertEquals( 1, config.enabledHttpConnectors().size() );

        HttpConnector httpConnector = config.enabledHttpConnectors().get( 0 );

        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ),
                config.get( httpConnector.listen_address ) );
    }

    @Test
    public void hasDefaultBookmarkAwaitTimeout()
    {
        Config config = Config.defaults();
        long bookmarkReadyTimeoutMs = config.get( GraphDatabaseSettings.bookmark_ready_timeout ).toMillis();
        assertEquals( TimeUnit.SECONDS.toMillis( 30 ), bookmarkReadyTimeoutMs );
    }

    @Test
    public void shouldBeAbleToOverrideHttpsListenAddressWithJustOneParameter() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap(
                "dbms.connector.https.enabled", "true",
                "dbms.connector.https.listen_address", ":8000" ) );

        // then
        assertEquals( 1, config.enabledHttpConnectors().size() );
        HttpConnector httpConnector = config.enabledHttpConnectors().get( 0 );

        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ),
                config.get( httpConnector.listen_address ) );
    }

    @Test
    public void throwsForIllegalBookmarkAwaitTimeout()
    {
        String[] illegalValues = { "0ms", "0s", "10ms", "99ms", "999ms", "42ms" };

        for ( String value : illegalValues )
        {
            try
            {
                Config config = Config.defaults( stringMap(
                        GraphDatabaseSettings.bookmark_ready_timeout.name(), value ) );
                config.get( GraphDatabaseSettings.bookmark_ready_timeout );
                fail( "Exception expected for value '" + value + "'" );
            }
            catch ( Exception e )
            {
                assertThat( e, instanceOf( InvalidSettingException.class ) );
            }
        }
    }

    @Test
    public void shouldDeriveListenAddressFromDefaultListenAddress() throws Exception
    {
        // given
        Config config = Config.fromSettings( stringMap( "dbms.connector.https.enabled", "true",
                "dbms.connector.http.enabled", "true",
                "dbms.connectors.default_listen_address", "0.0.0.0" ) ).withServerDefaults().build();

        // then
        assertEquals( 2, config.enabledHttpConnectors().size() );
        config.enabledHttpConnectors().forEach( c ->
                assertEquals( "0.0.0.0", config.get( c.listen_address ).getHostname() ) );
    }

    @Test
    public void shouldDeriveListenAddressFromDefaultListenAddressAndSpecifiedPorts() throws Exception
    {
        // given
        Config config = Config.defaults( stringMap( "dbms.connector.https.enabled", "true",
                "dbms.connector.http.enabled", "true",
                "dbms.connectors.default_listen_address", "0.0.0.0",
                "dbms.connector.http.listen_address", ":8000",
                "dbms.connector.https.listen_address", ":9000" ) );

        // then
        assertEquals( 2, config.enabledHttpConnectors().size() );

        config.enabledHttpConnectors().forEach( c ->
                {
                    if ( c.key().equals( "https" ) )
                    {
                        assertEquals( new ListenSocketAddress( "0.0.0.0", 9000 ),
                                config.get( c.listen_address ) );
                    }
                    else
                    {
                        assertEquals( new ListenSocketAddress( "0.0.0.0", 8000 ),
                                config.get( c.listen_address ) );
                    }
                }
        );
    }

    @Test
    public void shouldStillSupportCustomNameForHttpConnector() throws Exception
    {
        Config config = Config.defaults( stringMap(
                "dbms.connector.random_name_that_will_be_unsupported.type", "HTTP",
                "dbms.connector.random_name_that_will_be_unsupported.encryption", "NONE",
                "dbms.connector.random_name_that_will_be_unsupported.enabled", "true",
                "dbms.connector.random_name_that_will_be_unsupported.listen_address", ":8000" ) );

        // then
        assertEquals( 1, config.enabledHttpConnectors().size() );
        assertEquals( new ListenSocketAddress( "127.0.0.1", 8000 ),
                config.get( config.enabledHttpConnectors().get( 0 ).listen_address ) );
    }

    @Test
    public void shouldStillSupportCustomNameForHttpsConnector() throws Exception
    {
        Config config = Config.defaults( stringMap(
                "dbms.connector.random_name_that_will_be_unsupported.type", "HTTP",
                "dbms.connector.random_name_that_will_be_unsupported.encryption", "TLS",
                "dbms.connector.random_name_that_will_be_unsupported.enabled", "true",
                "dbms.connector.random_name_that_will_be_unsupported.listen_address", ":9000" ) );

        // then
        assertEquals( 1, config.enabledHttpConnectors().size() );
        assertEquals( new ListenSocketAddress( "127.0.0.1", 9000 ),
                config.get( config.enabledHttpConnectors().get( 0 ).listen_address ) );
    }
}
