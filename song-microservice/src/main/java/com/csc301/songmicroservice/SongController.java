package com.csc301.songmicroservice;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.pattern.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.List;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Null;
import javax.websocket.server.PathParam;

@RestController
@RequestMapping("/")
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();

	
	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		if (songId.isEmpty()){
			response.put("message", "a null value was passed into songId");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongTitleById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		if (songId.isEmpty()){
			System.out.println("hit the empty/null block");
			response.put("message", "a null value was passed into songId");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId);
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		return response;
	}

	
	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public @ResponseBody Map<String, Object> deleteSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));

		if (songId.isEmpty()){
			response.put("message", "a null value was passed into songId");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		Request songRequest = new Request.Builder()
				.url(String.format("http://localhost:3002/deleteAllSongsFromDb/" + songId))
				.method("PUT", okhttp3.RequestBody.create(null, new byte[0]))
				.addHeader("content-type", "application/json")
				.addHeader("Content-Length", "0").build();

		try {
			Response songResponse = client.newCall(songRequest).execute();
			// place the response body into a json object to access data key easily
			JSONObject requestJson = new JSONObject(songResponse.body().string());
			
			if ("INTERNAL_SERVER_ERROR".equals(requestJson.getString("status"))) {
				response.put("message", "An error occured while trying to remove songs from playlists");
				Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
				return response;
			}
			// Dont worry about 404 - If the song isnt in the database all is fine - thats what we want

		} catch (IOException e) {
			e.printStackTrace();
			response.put("message", "error occured while trying to delete from profile microservice");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}
		

		DbQueryStatus dbQueryStatus = songDal.deleteSongById(songId);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		return response;
	}

	
	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addSong(@RequestParam Map<String, String> params,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));

		System.out.println("params is " + params.keySet());
		if (!params.keySet().containsAll(List.from(new String[]{ "songName", "songArtistFullName", "songAlbum"}))){
			response.put("message", "Body missing information");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		Song songToAdd = new Song(params.get("songName"), params.get("songArtistFullName"), params.get("songAlbum"));

		if (params.get("songName").isEmpty() || params.get("songArtistFullName").isEmpty() || params.get("songAlbum").isEmpty()){
			response.put("message", "a null value was passed into one of the fields");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}
		
		DbQueryStatus status = songDal.addSong(songToAdd);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}

	
	@RequestMapping(value = "/updateSongFavouritesCount/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> updateFavouritesCount(@PathVariable("songId") String songId,
			@RequestParam("shouldDecrement") String shouldDecrement, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("data", String.format("PUT %s", Utils.getUrl(request)));

		if (songId.isEmpty()){
			response.put("message", "a null value was passed into songId");
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		if (!("true".equals(shouldDecrement) || "false".equals(shouldDecrement))) {
			response.put("message", "invalid boolean value");
			response = Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return response;
		}

		boolean shouldDecrementBoolean = Boolean.parseBoolean(shouldDecrement);
		DbQueryStatus status = songDal.updateSongFavouritesCount(songId, shouldDecrementBoolean);
		response.put("message", status.getMessage());
		response = Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
		return response;
	}
}