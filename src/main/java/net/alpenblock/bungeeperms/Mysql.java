package net.alpenblock.bungeeperms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.SneakyThrows;

public class Mysql
{

    public static void close(AutoCloseable res)
    {
        if (res == null)
        {
            return;
        }
        try
        {
            res.close();
        }
        catch (Exception e)
        {
        }
    }

    private final Config config;
    private final Debug debug;
    private final String configsection;
    private Connection connection;

    public Mysql(Config c, Debug d, String configsection)
    {
        config = c;
        debug = d;
        this.configsection = configsection;
    }

    public void connect()
    {
        String Host = BungeePerms.getInstance().getConfig().getString("Mysql.Host");
        String Port = BungeePerms.getInstance().getConfig().getString("Mysql.Port");
        String Databaseurl = BungeePerms.getInstance().getConfig().getString("Mysql.Database");
        String User = BungeePerms.getInstance().getConfig().getString("Mysql.User");
        String Password = BungeePerms.getInstance().getConfig().getString("Mysql.Password");

        BungeePerms.getInstance().getPlugin().getLogger().info("Connecting to database");
        try
        {
            //URL zusammenbasteln
            String url = "jdbc:mysql://" + Host + ":" + Port + "/" + Databaseurl + "?autoReconnect=true&dontTrackOpenResources=true" + "&username=" + User + "&password=" + Password;
            //, User, Password
            this.connection = DriverManager.getConnection(url);
        }
        catch (Exception e)
        {
            debug.log(e);
        }
    }

    public void close()
    {
        BungeePerms.getInstance().getPlugin().getLogger().info("Disconnecting from database");
        if (this.connection != null)
        {
            try
            {
                if (isConnected())
                {
                    this.connection.close();
                }
            }
            catch (Exception e)
            {
                debug.log(e);
            }
        }
    }

    public boolean isConnected()
    {
        boolean connected = false;

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = stmt("SELECT 1;");
            rs = this.returnQuery(stmt, false);
            if (rs == null)
                connected = false;
            else if (rs.next())
                connected = true;
        }
        catch (Exception e)
        {
            connected = false;
        }
        finally
        {
            close(rs);
            close(stmt);
        }
        return connected;
    }

    @SneakyThrows
    public PreparedStatement stmt(String template)
    {
        return connection.prepareStatement(template);
    }

    public ResultSet returnQuery(PreparedStatement stmt)
    {
        return returnQuery(stmt, true);
    }

    public boolean runQuery(PreparedStatement stmt)
    {
        return runQuery(stmt, true);
    }

    public long runQueryGetId(PreparedStatement stmt)
    {
        return runQueryGetId(stmt, true);
    }

    public boolean tableExists(String table)
    {
        boolean tableexists = false;

        PreparedStatement stmt = null;
        ResultSet res = null;
        try
        {
            checkConnection();
            stmt = stmt("SHOW TABLES");
            res = this.returnQuery(stmt);
            while (res.next())
            {
                if (res.getString(1).equalsIgnoreCase(table))
                {
                    tableexists = true;
                    break;
                }
            }
        }
        catch (Exception e)
        {
            debug.log(e);
        }
        finally
        {
            Mysql.close(res);
            Mysql.close(stmt);
        }
        return tableexists;
    }

    public boolean addColumn(String table, String column, String type, String after, String value)
    {
        boolean success = false;

        PreparedStatement stmt = null;
        ResultSet res = null;
        try
        {
            checkConnection();
            stmt = stmt("SHOW COLUMNS FROM " + table);
            res = returnQuery(stmt);

            boolean found = false;
            while (res.next())
            {
                if (res.getString("Field").equalsIgnoreCase(column))
                {
                    found = true;
                    break;
                }
            }
            stmt.close();
            if (!found)
            {
                checkConnection();
                stmt = stmt("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + type + " AFTER `" + after + "`");
                runQuery(stmt);
                stmt.close();
                
                checkConnection();
                stmt = stmt("UPDATE " + table + " SET " + column + "=?");
                stmt.setString(1, value);
                runQuery(stmt);
            }
            success = true;
        }
        catch (Exception e)
        {
            debug.log(e);
            success = false;
        }
        finally
        {
            Mysql.close(res);
            Mysql.close(stmt);
        }
        return success;
    }

    public int columnExists(String table, String column)
    {
        //0: error
        //1: column found
        //2: column not found
        int fsuccess = 2;

        PreparedStatement stmt = null;
        ResultSet res = null;
        try
        {
            checkConnection();
            stmt = stmt("SHOW COLUMNS FROM " + table);
            res = returnQuery(stmt);

            while (res.next())
            {
                if (res.getString("Field").equalsIgnoreCase(column))
                {
                    fsuccess = 1;
                }
            }
        }
        catch (Exception e)
        {
            debug.log(e);
            fsuccess = 0;
        }
        finally
        {
            close(res);
            close(stmt);
        }
        return fsuccess;
    }

    private ResultSet returnQuery(PreparedStatement stmt, boolean checkconnection)
    {
        ResultSet rs = null;
        try
        {
            if (checkconnection)
                checkConnection();
            rs = stmt.executeQuery();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
        }
        return rs;
    }

    private boolean runQuery(PreparedStatement stmt, boolean checkconnection)
    {
        try
        {
            if (checkconnection)
                checkConnection();
            boolean success = stmt.execute();
            return success;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private long runQueryGetId(PreparedStatement stmt, boolean checkconnection)
    {
        long id = 0;

        ResultSet rs = null;
        try
        {
            if (checkconnection)
                checkConnection();
            stmt.executeUpdate();

            rs = stmt.getGeneratedKeys();
            if (rs.last())
            {
                id = rs.getLong(1);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            close(rs);
        }
        return id;
    }

    public void checkConnection()
    {
        if (!isConnected())
        {
            reconnect();
        }
    }

    private void reconnect()
    {
        BungeePerms.getInstance().getPlugin().getLogger().info("Reconnecting to database");
        close();
        connect();
    }
}
