package com.csc301.profilemicroservice;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {

		StatementResult result = runQuery(
			String.format(
				"MATCH (p:playlist {plName:\"%s's playlist\"}) " +
				"MERGE (s:song {_id:\"%s\"}) " +
				"CREATE (p) -[:includes] -> (s)", 
				userName, songId));
		if (result == null) {
			return new DbQueryStatus("An error occured while adding the song to the playlist", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return new DbQueryStatus("Song successfully liked", DbQueryExecResult.QUERY_OK);
	}

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {

		StatementResult result = runQuery(
			String.format(
				"MATCH (:playlist {plName:\"%s's playlist\"}) -[i:includes]-> (:song {_id:\"%s\"}) DELETE i", 
				userName, songId));
		if (result == null) {
			return new DbQueryStatus("An error occured while adding the song to the playlist", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return new DbQueryStatus("Song successfully unliked", DbQueryExecResult.QUERY_OK);
	}

	@Override
	public DbQueryStatus deleteSongFromDb(String songId) {

		StatementResult result = runQuery(String.format("MATCH (s:song) WHERE s._id = '%s' RETURN s", songId));
		try {
			// single only runs properly if there is exactly one record - since userName is a primary key for nodes there can
			// be either 0 or 1 nodes matching this username
			result.single();
		} catch (NoSuchRecordException e) {
			return new DbQueryStatus("This song is not in the database", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		} catch (Exception e) {
			return new DbQueryStatus("An error occured while finding the somg to delete", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		result = runQuery(
			String.format("MATCH (s:song{_id:\"%s\"}) DETACH DELETE s", songId));
		if (result == null) {
			return new DbQueryStatus("An error occured while deleting the song from the database", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return new DbQueryStatus("Song successfully deleted from database", DbQueryExecResult.QUERY_OK);
	}

	public Boolean likesSong(String userName, String songId) {
		StatementResult result = runQuery(
			String.format("RETURN EXISTS( (:playlist {plName: \"%s's playlist\"})-[:includes]->(:song {_id:\"%s\"}) )", userName, songId));
		if (result == null) {
			return null;
		}
		Record record = result.single();
		return record.get(0).asBoolean();
	}

	private StatementResult runQuery(String queryStr) {
		StatementResult result = null;
		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				result = trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			session.close();
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
