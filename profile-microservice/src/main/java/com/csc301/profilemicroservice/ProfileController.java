package com.csc301.profilemicroservice;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.csc301.profilemicroservice.Utils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class ProfileController {
	public static final String KEY_USER_NAME = "userName";
	public static final String KEY_USER_FULLNAME = "fullName";
	public static final String KEY_USER_PASSWORD = "password";

	@Autowired
	private final ProfileDriverImpl profileDriver;

	@Autowired
	private final PlaylistDriverImpl playlistDriver;

	OkHttpClient client = new OkHttpClient();

	public ProfileController(ProfileDriverImpl profileDriver, PlaylistDriverImpl playlistDriver) {
		this.profileDriver = profileDriver;
		this.playlistDriver = playlistDriver;
	}

	@RequestMapping(value = "/profile", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addSong(
			@org.springframework.web.bind.annotation.RequestParam Map<String, String> params,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));

		// check if there are any query parameters missing
		if (!params.keySet().containsAll(List.from(new String[] { "userName", "fullName", "password" }))) {
			response.put("message", "Body missing information");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		// check the parameters for empty strings
		if ( params.get("userName").isEmpty() || params.get("fullName").isEmpty()|| params.get("password").isEmpty()){
			response.put("message", "a null value was passed into the body in one of the fields");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		DbQueryStatus status = profileDriver.createUserProfile(params.get("userName"), params.get("fullName"),
				params.get("password"));
		
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	@RequestMapping(value = "/followFriend/{userName}/{friendUserName}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> followFriend(@PathVariable("userName") String userName,
			@PathVariable("friendUserName") String friendUserName, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		DbQueryStatus status = profileDriver.followFriend(userName, friendUserName);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	@RequestMapping(value = "/getAllFriendFavouriteSongTitles/{userName}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getAllFriendFavouriteSongTitles(@PathVariable("userName") String userName,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		// check if user exists within the database
		Boolean userExists = profileDriver.checkProfileExists(userName);
		if (userExists == null){
			response.put("message", String.format("an error occured while trying to find user '%s'", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		} else if (!userExists) {
			response.put("message", String.format("user '%s' cannot be found in the database", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_NOT_FOUND, null);
			return response;
		}

		DbQueryStatus status = profileDriver.getAllSongFriendsLike(userName);

		Map<String, java.util.List<String>> data =  (Map<String, java.util.List<String>>) status.getData();

		for (String key : data.keySet()) {
			java.util.List<String> songs = data.get(key);
			for (int i = 0; i < data.get(key).size(); i++) {
				// ping the api here to get the name of the song
				// form the request
				String songId = songs.get(i);
				Request songRequest = new Request.Builder()
						.url(String.format("http://localhost:3001/getSongTitleById/" + songId))
						.method("GET", null)
						.addHeader("content-type", "application/json").addHeader("Content-Length", "0").build();
	
				try {
					// create client and execute request
					OkHttpClient client = new OkHttpClient();
					Response songResponse = client.newCall(songRequest).execute();
					// place the response body into a json object to access data key easily
					JSONObject requestJson = new JSONObject(songResponse.body().string());
					if ("INTERNAL_SERVER_ERROR".equals(requestJson.get("status"))) {
						response.put("message", "IOException occured while trying to send a request for song titles");
						Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
						return response;
					}
					if ("NOT_FOUND".equals(requestJson.get("status"))) {
						response.put("message", String.format("The song with id '%s' was not found in the song database", songId));
						Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
						return response;
					}
					String songName = requestJson.getString("data");
					// append the song to the songNames list
					songs.set(i, songName);
				} catch (IOException e) {
					response.put("message", "IOException occured while trying to send a request for song titles");
					Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
					return response;
				} catch (JSONException e) {
					response.put("message", "Error parsing JSON body");
					Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
					return response;
				} catch (Exception e) {
					response.put("message", "Error occured while trying to send a request for song titles");
					Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
					return response;
				}
			}
		}




		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
		
	}

	@RequestMapping(value = "/unfollowFriend/{userName}/{friendUserName}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> unfollowFriend(@PathVariable("userName") String userName,
			@PathVariable("friendUserName") String friendUserName, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		DbQueryStatus status = profileDriver.unfollowFriend(userName, friendUserName);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	@RequestMapping(value = "/likeSong/{userName}/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> likeSong(@PathVariable("userName") String userName,
			@PathVariable("songId") String songId, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		Boolean userExists = profileDriver.checkProfileExists(userName);
		if (userExists == null) {
			response.put("message", String.format("An error occured while finding user '%s'", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		} else if (!userExists) {
			response.put("message", String.format("User '%s' does not exist", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_NOT_FOUND, null);
			return response;
		}

		Boolean alreadyLiked = playlistDriver.likesSong(userName, songId);
		if (alreadyLiked == null) {
			response.put("message", "Error while checking if song has been liked already");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		} else if (alreadyLiked) {
			response.put("message","Song has already been liked");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		Request songRequest = new Request.Builder()
				.url(String.format("http://localhost:3001/updateSongFavouritesCount/%s?shouldDecrement=false", songId))
				.method("PUT", RequestBody.create(null, new byte[0])).addHeader("content-type", "application/json")
				.addHeader("Content-Length", "0").build();

		try {
			Response songResponse = client.newCall(songRequest).execute();
			
			// jumps straight to catch block. How useful!
			if (!songResponse.isSuccessful()) throw new IOException("Unexpected code " + response);
			String status = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.readValue(songResponse.body().string(), MyResponseStatus.class).getStatus();
			if ("INTERNAL_SERVER_ERROR".equals(status)) {
				response.put("message", "An error occured while trying to like a song");
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
				return response;
			}
			if ("NOT_FOUND".equals(status)) {
				response.put("message", String.format("The song with id '%s' was not found", songId));
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_NOT_FOUND, null);
				return response;
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("message", "An error occured while trying to send the request to like a song");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		DbQueryStatus status = playlistDriver.likeSong(userName, songId);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	@RequestMapping(value = "/unlikeSong/{userName}/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> unlikeSong(@PathVariable("userName") String userName,
			@PathVariable("songId") String songId, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		Boolean userExists = profileDriver.checkProfileExists(userName);
		if (userExists == null) {
			response.put("message", String.format("An error occured while finding user '%s'", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		} else if (!userExists) {
			response.put("message", String.format("User '%s' does not exist", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_NOT_FOUND, null);
			return response;
		}

		Boolean alreadyLiked = playlistDriver.likesSong(userName, songId);

		Request songRequest = new Request.Builder()
				.url(String.format("http://localhost:3001/updateSongFavouritesCount/%s?shouldDecrement=true", songId))
				.method("PUT", RequestBody.create(null, new byte[0])).addHeader("content-type", "application/json")
				.addHeader("Content-Length", "0").build();

		try {
			Response songResponse = client.newCall(songRequest).execute();
			// jumps straight to catch block. How useful!
			if (!songResponse.isSuccessful()) throw new IOException("Unexpected code " + response);
			String status = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.readValue(songResponse.body().string(), MyResponseStatus.class).getStatus();
			if ("INTERNAL_SERVER_ERROR".equals(status)) {
				response.put("message", String.format("An error occured while trying to unlike the song with id %s", songId));
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
				return response;
			}
			if ("NOT_FOUND".equals(status)) {
				response.put("message", String.format("The song with id '%s' was not found", songId));
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_NOT_FOUND, null);
				return response;
			}
			if (alreadyLiked == null) {
				response.put("message", "Error while checking if song has been liked already");
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
				return response;
			}
			if (!alreadyLiked) {
				response.put("message","This song isnt liked by this user");
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
				return response;
			}
			

		} catch (Exception e) {
			e.printStackTrace();
			response.put("message", "An error occured while trying to send the request to unlike a song");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}
		
		/* DbQueryStatus removal = playlistDriver.deleteSongFromDb(songId);
		if (removal == null){
			response.put("message", String.format("An error occured while trying to delete song from Neo4j", userName));
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		} */
		

		DbQueryStatus status = playlistDriver.unlikeSong(userName, songId);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	// doesn't actually delete all songs from db, its a typo
	// only deletes the song respective to the songId passed in
	@RequestMapping(value = "/deleteAllSongsFromDb/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> deleteAllSongsFromDb(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		
		DbQueryStatus status = playlistDriver.deleteSongFromDb(songId);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	
}