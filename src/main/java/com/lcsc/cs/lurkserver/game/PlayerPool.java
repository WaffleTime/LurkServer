package com.lcsc.cs.lurkserver.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by Jake on 4/19/2015.
 * This class will be stored within Game and will hold the player objects that are logged into the game. If a
 * player has just logged in it will have a new player object with defaults assigned to it. If an existing player
 * logs in, its data will be loaded from its player file and its information will be used again.
 */
public class PlayerPool {
    private static final Logger                 _logger = LoggerFactory.getLogger(PlayerPool.class);
    private              GameMap                _map;
    //These are the players that are logged in.
    private              Map<String, Player>    _players;

    //This is an absolute path!
    private final   String _playerDataDirectory;

    public PlayerPool(GameMap map) {
        _map                    = map;
        _players                = new TreeMap<String, Player>();

        String projRoot         = new File("").getAbsolutePath();
        File playerDataDir      = new File(projRoot, "data/player_data");
        _playerDataDirectory    = playerDataDir.getAbsolutePath();

        playerDataDir.mkdirs();
    }

    /**
     * This is for fetching data of players that are in the game currently.
     * @param playerName The player name that the Player object belongs to. It should be unique.
     * @return If the player exists a Player object will be returned, otherwise a null will be returned.
     */
    public synchronized Player getPlayer(String playerName) {
        return _players.get(playerName);
    }

    public synchronized void removePlayer(String playerName) {
        getPlayer(playerName).saveData();
        _players.remove(playerName);
    }

    /**
     * This is for the QUERY command. We need a list of the active players for the response.
     * @param excludedPlayerName This player will be excluded from the list.
     * @return A list of the active players!
     */
    public synchronized String getPlayerList(String excludedPlayerName) {
        String playerList = "";
        for (String playerName : _players.keySet()) {
            if (!playerName.equals(excludedPlayerName))
                playerList += "Player: "+playerName+"\n";
        }
        return playerList;
    }

    /**
     * This checks the PlayerPool _players map to see if the player is logged in currently.
     * @return false if player isn't logged in, true if the player is logged in.
     */
    public synchronized boolean playerLoggedIn(String playerName) {
        return _players.containsKey(playerName);
    }



    /**
     * This is called when a client logs in as a player. This playerName might have been used before. If it has
     * and that player isn't logged in currently, the players data will be loaded from a file. If it hasn't been used
     * before then the player will be given default data.
     * @param playerName A unique player name that may or may not already exist.
     * @return A response that will be sent back to the client. It has to follow the LurkProtocol.
     */
    public synchronized ResponseMessage loadPlayer(String playerName) {
        ResponseMessage response = null;

        if (!playerLoggedIn(playerName)) {
            if (Player.playerExists(playerName, _playerDataDirectory)) {
                response = ResponseMessage.REPRISING_PLAYER;
            }
            else {
                response = ResponseMessage.NEW_PLAYER;
            }

            Player newPlayer    = new Player(playerName, _playerDataDirectory, _map.getStartingRoom());

            if (newPlayer.isDead())
                response = ResponseMessage.DEAD_PLAYER;
            else {
                _players.put(playerName, newPlayer);
                _map.spawnPlayer(newPlayer);
            }
        }
        else {
            response = ResponseMessage.NAME_TAKEN;
            _logger.warn("The ClientPool shouldn't be trying to load players that are currently in the game!");
        }

        return response;
    }

    /**
     * This should be called periodically and will inform the Player objects to save their data to their files.
     */
    public synchronized void savePlayers() {
        for (Player player : _players.values()) {
            player.saveData();
        }
    }
}
