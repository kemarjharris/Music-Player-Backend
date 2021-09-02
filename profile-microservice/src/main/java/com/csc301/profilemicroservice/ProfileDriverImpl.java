package com.csc301.profilemicroservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		StatementResult result = runQuery(String.format(
				"CREATE (nProfile:profile {userName:\"%s\", fullName:\"%s\", password:\"%s\"})"
						+ "-[:created]->(nPlaylist:playlist {plName:nProfile.userName + \"'s playlist\"})",
				userName, fullName, password));
		if (result == null) {
			return new DbQueryStatus("An error occured while trying to add this profile",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return new DbQueryStatus("Profile successfully added", DbQueryExecResult.QUERY_OK);
	}

	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {

		Boolean userExists = checkProfileExists(userName);
		Boolean friendExists = checkProfileExists(frndUserName);
		if (userExists == null || friendExists == null) {
			return new DbQueryStatus("Error occured during user look up", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		if (!userExists || !friendExists) {
			return new DbQueryStatus(
					String.format("Either user %s or friend %s does not exist", userName, frndUserName),
					DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		}
		if (userName.equals(frndUserName)) {
			return new DbQueryStatus("Users are not allowed to follow themselves!", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		Boolean alreadyFollows = alreadyFollows(userName, frndUserName);
		if (alreadyFollows == null) {
			return new DbQueryStatus("Error occured during following relationship look up",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		} else if (alreadyFollows) {
			return new DbQueryStatus(String.format("%s already follows %s", userName, frndUserName),
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		StatementResult result = runQuery(
				String.format("MATCH (a:profile),(b:profile) " + "WHERE a.userName = '%s' AND b.userName = '%s'"
						+ "CREATE (a)-[r:follows]->(b)", userName, frndUserName));

		if (result == null) {
			return new DbQueryStatus("An error occured while trying to follow the user",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		return new DbQueryStatus(String.format("'%s' has successfully followed '%s'", userName, frndUserName),
				DbQueryExecResult.QUERY_OK);
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {

		Boolean userExists = checkProfileExists(userName);
		Boolean friendExists = checkProfileExists(frndUserName);
		if (userExists == null || friendExists == null) {
			return new DbQueryStatus("Error occured during user look up", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		if (!userExists || !friendExists) {
			return new DbQueryStatus(
					String.format("Either user %s or friend %s does not exist", userName, frndUserName),
					DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		}
		Boolean follows = alreadyFollows(userName, frndUserName);
		if (follows == null) {
			return new DbQueryStatus("Error occured during following relationship look up",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		} else if (!follows) {
			return new DbQueryStatus(String.format("%s does not follow %s", userName, frndUserName),
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		StatementResult result = runQuery(String.format(
				"MATCH (a:profile {userName : '%s'})-[r:follows]->(b:profile{userName : '%s'}) " + "DELETE r", userName,
				frndUserName));

		if (result == null) {
			return new DbQueryStatus("An error occured while trying to follow the user",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		return new DbQueryStatus(String.format("'%s' has successfully unfollowed '%s'", userName, frndUserName),
				DbQueryExecResult.QUERY_OK);
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {

		// check if profile exists
		Boolean profileExists = checkProfileExists(userName);
		if (profileExists == null) {
			return new DbQueryStatus("Error occured while trying to find user", DbQueryExecResult.QUERY_ERROR_GENERIC);
		} else if (!profileExists) {
			return new DbQueryStatus("User " + userName + " does not exist", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		// retrieves all of the playlists of those who are followed by the passed in
		// userName
		StatementResult result = runQuery(String.format(
				"MATCH(:profile{userName:\"%s\"})-[:follows]->(:profile)-[:created]->(p:playlist) RETURN p", userName));
		if (result == null) {
			return new DbQueryStatus("Error occured while trying to find user's friend's playlists",
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		// store the users and their songs in this list
		Map<String, List<String>> friendCollection = new HashMap<String, List<String>>(); 
 		// iterate through the playlists of every followed user
		while (result.hasNext()) {
			// first iteration lets you move to the first playlist, then normally iterate
			Record record = result.next();
			// get playlist name and username to be printed in the response body
			String friendPlaylistName = record.get("p").asNode().get("plName").asString();
			System.out.println(friendPlaylistName);
			String friendUserName = friendPlaylistName.substring(0, friendPlaylistName.lastIndexOf("'s playlist"));
			System.out.println(friendUserName);
			// run a query to get all the songId associated with a single playlist
			StatementResult songIdList = runQuery(String.format(
					"MATCH(:profile{userName:\"%s\"})-[:follows]->(:profile)-[:created]->(p:playlist{plName:\"%s\"})-[:includes]->(s:song) RETURN s",
					userName, friendPlaylistName));
			if (songIdList == null) {
				return new DbQueryStatus("Error occured while trying to access the song ids within the playlist",
						DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
			// iterate through all the song ids of a playlist
			List<String> songNames = new ArrayList<String>();
			while (songIdList.hasNext()) {
				Record current = songIdList.next();
				// grab songId to use in api ping request
				String songId = current.get("s").asNode().get("_id").asString();

				songNames.add(songId);
			}
			// add the list of song names generated in the inner while loop
			friendCollection.put(friendUserName, songNames);		
		}
		System.out.println(friendCollection.toString());
		
		DbQueryStatus completed = new DbQueryStatus("Successfully extracted collection of friends songs", DbQueryExecResult.QUERY_OK);
		completed.setData(friendCollection);
		return completed;
	}

	private Boolean alreadyFollows(String userName, String frndUserName) {
		StatementResult result = runQuery(
			String.format("RETURN EXISTS( (:profile {userName: '%s'})-[:follows]->(:profile {userName:'%s'}) )", userName, frndUserName));
		if (result == null) {
			return null;
		}

		Record record = result.single();
		return record.get(0).asBoolean();

	}

	public Boolean checkProfileExists(String userName) {
		StatementResult result = runQuery(String.format("MATCH (n:profile) WHERE n.userName = '%s' RETURN n", userName));
		try {
			// single only runs properly if there is exactly one record - since userName is a primary key for nodes there can
			// be either 0 or 1 nodes matching this username
			if (result == null) return null;
			result.single();
			return true;
		} catch (NoSuchRecordException e) {
			e.printStackTrace();
			return false;
		} 
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
