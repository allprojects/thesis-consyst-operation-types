package de.tuda.stg.consys.demo

import java.net.InetSocketAddress

import com.datastax.oss.driver.api.core.CqlSession

object CassandraDemo extends App {

	val cassandraSession = CqlSession.builder()
		.addContactPoint(InetSocketAddress.createUnresolved("127.0.0.1", 9042))
		.withLocalDatacenter("datacenter1")
		.build()

	cassandraSession.execute("CREATE KEYSPACE IF NOT EXISTS my_keyspace WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor' : 3};")
	cassandraSession.execute("USE my_keyspace;")
	cassandraSession.execute("CREATE TABLE test;")

}
