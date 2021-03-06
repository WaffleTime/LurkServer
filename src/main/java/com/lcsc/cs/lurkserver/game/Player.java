package com.lcsc.cs.lurkserver.game;

import com.lcsc.cs.lurkserver.Protocol.CommandType;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jake on 4/19/2015.
 * This holds a logged in player's data.
 */
public class Player implements Being{
    private static final Logger     _logger         = LoggerFactory.getLogger(Player.class);
    private final   int             MAX_STAT_POINTS = 100;
    private final   int             MAX_HEALTH      = 100;
    private final   File            _playerFile;
    //This is the player's client!
    private         Client          _client;
    public          String          name;
    private         String          _description;
    private         int             _gold;
    private         int             _attack;
    private         int             _defense;
    private         int             _regen;
    private         BeingStatus     _status;
    private         String          _location;
    private         int             _health;
    private         boolean         _started;
    private         List<String>    _keys;

    /**
     * The constructor for the player. This will automatically load the player's data from its existing data file
     * or will load in default data and save the file.
     * @param playerName A unique player name.
     * @param playerDataDir The absolute path for the players' data files directory.
     * @param startingRoom This is the room that the player starts out in.
     */
    public Player(String playerName, String playerDataDir, String startingRoom) {
        name            = playerName;
        _playerFile     = new File(playerDataDir, playerName+".pldat");

        if (_playerFile.exists()) {
            loadDataFromFile(startingRoom);
        }
        else {
            loadDefaultData(startingRoom);
        }
    }

    /**
     * This will regenerate the player's health.
     * @param secondsPassed This is how much time has passed.
     */
    public synchronized void regenHealth(int secondsPassed) {
        int regeneratedHealth = ((Double)((secondsPassed/10.)*(double)_regen)).intValue();
        if (regeneratedHealth + _health > MAX_HEALTH)
            _health = MAX_HEALTH;
        else
            _health += regeneratedHealth;
    }

    /**
     * This tells the pool if the player exists or not.
     * @return A boolean specifying if the player's data file exists already.
     */
    public static boolean playerExists(String playerName, String playerDataDir) {
        return new File(playerDataDir, playerName+".pldat").exists();
    }

    public synchronized boolean isDead() {
        return _status == BeingStatus.DEAD;
    }

    /**
     * This gets the attack of the player so damage can be done to an enemy.
     * @return The value of the player's attack or 0 if the player is dead.
     */
    @Override
    public synchronized int getAttack() {
        return isDead() ? 0 : _attack;
    }

    /**
     * This is called when an enemy is attacking the player.
     * @param damage This is the attack of the enemy in addition to the d20 roll that was obtained.
     *               If a 1 was rolled, this method will not be called and damage will be done to the user
     *               instead.
     * @return The amount of gold dropped is returned. Gold is only dropped if the player has died.
     */
    @Override
    public synchronized int doDamage(int damage) {
        if (damage > _defense)
            _health -= damage-_defense;

        int gold = 0;
        if (_health <= 0) {
            _status = BeingStatus.DEAD;
            gold    = _gold;
            _gold   = 0;
        }
        return gold;
    }

    /**
     * This is called each time this Being does damage to another Being.
     * @param gold This is the gold that is picked up after damage is done to another Being. If the other
     *             Being isn't dead, then zero gold will be passed to this method.
     */
    @Override
    public synchronized void pickedUpGold(int gold) {
        _gold += gold;
        _client.sendStatus(_health, gold);
    }

    /**
     * Checks to see if the player has a key that belongs to some door.
     * @param keyName The name of the key.
     * @return true if the player has the key, false otherwise.
     */
    public synchronized boolean hasKey(String keyName) {
        return _keys.contains(keyName);
    }

    /**
     * This is called when the PCKUP extension is used.
     * @param keyName The name of the key that is being picked up.
     * @return true if the player doesn't have the key already, false otherwise.
     */
    public synchronized boolean pickUpKey(String keyName) {
        boolean success = false;
        if (!_keys.contains(keyName)) {
            _keys.add(keyName);
            success = true;
        }
        return success;
    }

    /**
     * This sets up a way for us to contact the client using the Player's object.
     * @param client The client that can communicate with the user.
     */
    public void setClient(Client client) {
        _client = client;
    }

    /**
     * This will send the current room info to the client, which will send it to the user.
     * @param infoList A list of the current things in the room.
     */
    public synchronized void sendRoomInfo(List<String> infoList) {
        _client.sendRoomInfo(infoList);
    }

    /**
     * This just sets some default data for the new player.
     * @param startingRoom This is the room that the player starts out in.
     */
    private void loadDefaultData(String startingRoom) {
        _description = null;
        _gold        = 0;
        _attack      = 0;
        _defense     = 0;
        _regen       = 0;
        _status      = BeingStatus.ALIVE;
        _location    = startingRoom;
        _health      = MAX_HEALTH;
        _started     = false;
        _keys        = new ArrayList<String>();
    }

    /**
     * This will load the player's data from a file.
     * @param startingRoom This is the room that the player starts out in.
     */
    private void loadDataFromFile(String startingRoom) {
        FileReader reader = null;

        try {
            reader = new FileReader(_playerFile);
            Map<String, Object> data = (Map<String, Object>)JSON.parse(reader);

            _description = (String)data.get("description");
            _gold        = ((Long)data.get("gold")).intValue();
            _attack      = ((Long)data.get("attack")).intValue();
            _defense     = ((Long)data.get("defense")).intValue();
            _regen       = ((Long)data.get("regen")).intValue();
            _status      = BeingStatus.fromString((String) data.get("status"));
            _location    = startingRoom;
            _health      = ((Long)data.get("health")).intValue();
            _started     = ((Boolean)data.get("started")).booleanValue();

            _keys        = new ArrayList<String>();
        } catch (FileNotFoundException e) {
            _logger.error("Problem loading the player data file", e);
        } catch (IOException e) {
            _logger.error("Problem loading the player data file", e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {}
        }
    }

    /**
     * This is used to save the player's current data to a file so it can be loaded next time that player joins
     * the game.
     */
    public synchronized void saveData() {
        if (_started) {
            Map<String, Object> data = new HashMap<String, Object>();

            data.put("description", _description);
            data.put("gold", _gold);
            data.put("attack", _attack);
            data.put("defense", _defense);
            data.put("regen", _regen);
            data.put("status", _status.getStatus());
            data.put("health", _health);
            data.put("started", _started);

            String jsonData = JSON.toString(data);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(_playerFile);
                out.write(jsonData.getBytes());
            } catch (FileNotFoundException e) {
                _logger.error("Player data file failed to save", e);
            } catch (IOException e) {
                _logger.error("Player data file failed to save", e);
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * This will respond to the Start command essentially.
     * @return A boolean saying whether this player is ready to be started.
     */
    public boolean start() {
        boolean ready = true;
        if (_description == null ||
                (_attack == 0 && _defense == 0 && _regen == 0)) {
            ready = false;
        }
        else
            _started = true;
        return ready;
    }

    /**
     * This returns the location of the player.
     * @return A string name of the player's current room.
     */
    public synchronized String currentRoom() {
        return _location;
    }

    /**
     * Changes the location of the player.
     * @param newRoom This is the new location of the player.
     */
    public synchronized void changeRoom(String newRoom) {
        _location = newRoom;
    }

    /**
     * This is for setting the stats of the player before the player has restarted yet.
     * @param commandType This specifies the stat that is being changed.
     * @param stat This is the actual stat that some stat is being changed to.
     * @return A response that will be sent to the client.
     */
    public ResponseMessage setStat(CommandType commandType, String stat) {
        ResponseMessage response = ResponseMessage.FINE;
        if (commandType == CommandType.SET_PLAYER_DESC) {
            _description = stat;

        }
        else if (commandType == CommandType.SET_ATTACK_STAT) {
            try {
                int remainingStatPoints = MAX_STAT_POINTS - _defense - _regen;
                int atkStat = Integer.parseInt(stat);
                if (atkStat >= 0 && atkStat <= remainingStatPoints)
                    _attack = atkStat;
                else
                    response = ResponseMessage.STATS_TOO_HIGH;
            } catch(Exception e) {
                response = ResponseMessage.INCORRECT_STATE;
            }
        }
        else if (commandType == CommandType.SET_DEFENSE_STAT) {
            try {
                int remainingStatPoints = MAX_STAT_POINTS - _attack - _regen;
                int defStat = Integer.parseInt(stat);
                if (defStat >= 0 && defStat <= remainingStatPoints)
                    _defense = defStat;
                else
                    response = ResponseMessage.STATS_TOO_HIGH;
            } catch(Exception e) {
                response = ResponseMessage.INCORRECT_STATE;
            }
        }
        else if (commandType == CommandType.SET_REGEN_STAT) {
            try {
                int remainingStatPoints = MAX_STAT_POINTS - _attack - _defense;
                int regStat = Integer.parseInt(stat);
                if (regStat >= 0 && regStat <= remainingStatPoints)
                    _regen = regStat;
                else
                    response = ResponseMessage.STATS_TOO_HIGH;
            } catch(Exception e) {
                response = ResponseMessage.INCORRECT_STATE;
            }
        }

        return response;
    }

    public synchronized String getInfo() {
        /*
        Name:  Trudy
        Description: Black Hat Security Expert
        Gold: 10
        Attack: 60
        Defense: 30
        Regen: 10
        Status: ALIVE
        Location: Broom Closet
        Health: 100
        Started:  YES
        */

        return  String.format("Name: %s\n",name)+
                String.format("Description: %s\n", _description)+
                String.format("Gold: %d\n", _gold)+
                String.format("Attack: %d\n", _attack)+
                String.format("Defense: %d\n", _defense)+
                String.format("Regen: %d\n", _regen)+
                String.format("Status: %s\n", _status.getStatus())+
                String.format("Location: %s\n", _location)+
                String.format("Health: %d\n", _health)+
                String.format("Started: %s", _started ? "YES" : "NO");
    }
}
