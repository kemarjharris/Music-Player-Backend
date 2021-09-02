package com.csc301.songmicroservice;

import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		try {
			Song song = db.insert(songToAdd);
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("Song was added successfully", DbQueryExecResult.QUERY_OK);
			databaseQueryStatus.setData(song);
			return databaseQueryStatus;
		} catch (Exception e) {
			return new DbQueryStatus("An error occured while adding the song", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus findSongById(String songId) {
		try {
			Song song = db.findById(new ObjectId(songId), Song.class);
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("Song was found successfully", DbQueryExecResult.QUERY_OK);
			databaseQueryStatus.setData(song);
			return databaseQueryStatus;
		} catch (Exception e) {
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("An error occured", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return databaseQueryStatus;
		}
	}

	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		try {
			if (songId.isEmpty()){
				return new DbQueryStatus("null value given for songId", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
			Song song = db.findById(new ObjectId(songId), Song.class);
			if (song == null) {
				return new DbQueryStatus("Song with id " + songId + " was not found",
				DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			DbQueryStatus status = new DbQueryStatus("Song succesfully found", DbQueryExecResult.QUERY_OK);
			status.setData(song.getSongName());
			return status;

		} catch (Exception e) {
			return new DbQueryStatus("An error occured", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus deleteSongById(String songId) {
		try {
			if (db.findById(new ObjectId(songId), Song.class) == null) {
				return new DbQueryStatus("Song with id " + songId + " was not found",
				DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}

			Song songToDelete = new Song("","","");
			songToDelete.setId(new ObjectId(songId));
			DeleteResult result = db.remove(songToDelete);
		
			if (!result.wasAcknowledged()) {
				DbQueryStatus databaseQueryStatus = new DbQueryStatus("An error occured", DbQueryExecResult.QUERY_ERROR_GENERIC);
				return databaseQueryStatus;
			} 
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("Song successfully deleted", DbQueryExecResult.QUERY_OK);
				return databaseQueryStatus;
		} catch (Exception e) {
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("An error occured", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return databaseQueryStatus;
		}
	}

	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId, boolean shouldDecrement) {
		try {
			Song song = db.findById(new ObjectId(songId), Song.class);
			if (song == null) {
				return new DbQueryStatus("Song with id " + songId + " was not found",
				DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}

			if (song.getSongAmountFavourites() <= 0 && shouldDecrement) {
				return new DbQueryStatus("cannot decrement below 0", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}

			db.findAndModify(Query.query(Criteria.where("_id").is(songId)),
				Update.update("songAmountFavourites", shouldDecrement ? 
				song.getSongAmountFavourites() -1:
				song.getSongAmountFavourites() +1),
				Song.class);

			Song updatedSong = db.findById(new ObjectId(songId), Song.class);
			if ((shouldDecrement && updatedSong.getSongAmountFavourites() != song.getSongAmountFavourites() -1)||
				(!shouldDecrement && updatedSong.getSongAmountFavourites() != song.getSongAmountFavourites() +1)) {
					return new DbQueryStatus("An error occured, song changed to incorrect value", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

			return new DbQueryStatus("Song favourite value updated", DbQueryExecResult.QUERY_OK);
			
		} catch (Exception e) {
			DbQueryStatus databaseQueryStatus = new DbQueryStatus("An error occured", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return databaseQueryStatus;
		}
	}
}