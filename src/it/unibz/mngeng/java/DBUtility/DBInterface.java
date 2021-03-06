package it.unibz.mngeng.java.DBUtility;

import it.unibz.mngeng.java.Commons.Utility;
import it.unibz.mngeng.java.Exceptions.RMException;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

public class DBInterface implements Serializable
{
	static final long serialVersionUID = 1;

	protected String tableName = "";
	protected boolean readPage = false;
	protected boolean showNext = false;
	protected boolean showPrevious = false;
	protected int startRecord = 0;
	protected boolean changesToJournal = false;
	private static DBConnection conn = null;
	
	static Logger logger = Logger.getLogger(DBInterface.class);
	
	private String quoteString(String strToQuote)
	{
		String strQuoted = strToQuote;
		int	offset = -1;
		int i = 0; 
		while(i >= 0)
		{
			if ((offset = strQuoted.indexOf("'", i)) >= 0)
			{
				strQuoted = strQuoted.substring(0, offset) + "'" + strQuoted.substring(offset);
				i = offset + 2;
			}
			else
			{
				i = -1;
			}
		}
		return strQuoted;
	}

	public static int numberOfRecords(String table, String where) throws RMException 
	{
		String retVal = null;
		int count = -1;
		
		String sql = "SELECT COUNT(*) as count FROM " + table + " WHERE " + where;
		conn = DBConnection.getInstance();
    	conn.executeQuery(sql);
		ResultSet rs = conn.getRs();
		try 
		{
			if(rs.next())
				count = rs.getInt("count");
		}
		catch (SQLException e) 
		{
			throw new RMException(retVal);
		}
		return(count);
	}
	
    protected static Field[] getAllFields(Class<?> cType)
	{
		List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = cType; c != null; c = c.getSuperclass()) 
        {
        	List<Field> tempList = new ArrayList<Field>();
        	tempList.addAll(Arrays.asList(c.getDeclaredFields()));
        	for(int y = tempList.size() - 1; y >= 0 ; y--)
        	{
        		if ((tempList.get(y).getModifiers() & java.lang.reflect.Modifier.PROTECTED) != java.lang.reflect.Modifier.PROTECTED)
        			tempList.remove(y);
        	}
        	if (tempList.size() > 0)
        		fields.addAll(tempList);
        }
        Field[] fieldArr = new Field[fields.size()];
        return fields.toArray(fieldArr);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public String getUpdateStatement(String sql, Object tbObj, String avoidColumn) throws RMException
	{
    	String sSep = "";
    	String[] avoidCols = null;
    	String retVal = null;
    	String retSql;

    	if (avoidColumn.indexOf(";") > 0)
    	{
			avoidCols = avoidColumn.split(";");
			for( int y = 0; y < avoidCols.length; y++)
			{
				avoidCols[y] = avoidCols[y].trim();
			}
    	}
    	else
    	{
			avoidCols = new String[1];
			avoidCols[0] = avoidColumn;
    	}
		
		conn = DBConnection.getInstance();
    	conn.executeQuery(sql);

    	retSql = "";
		ResultSetMetaData rsm = conn.getRsm();
    	int y, columnCount;
		try
		{
			columnCount = rsm.getColumnCount();
		}
		catch (SQLException e) 
		{
			retSql = "Error " + e.getStackTrace().getClass().getSimpleName() + " retrieving column count from result set"; 
			throw new RMException(retSql);
		}
    	
    	Field[] clFields = getAllFields(tbObj.getClass());
		for(int i = 1; i <= columnCount; i++)
		{
			String columnName;
			int columnType;
			try 
			{
				columnType = rsm.getColumnType(i);
				columnName = rsm.getColumnName(i);
			}
			catch (SQLException e) 
			{
				retSql = "Error " + e.getStackTrace().getClass().getSimpleName() + " retrieving column details"; 
				throw new RMException(retSql);
			}
			
			// Check if the column is in the avoid pool
			for(y = 0; y < avoidCols.length; y++)
			{
				if(columnName.compareTo(avoidCols[y]) == 0)
				{
					break;
				}
			}
			if (y < avoidCols.length)
			{
				continue;
			}

			for(y = 0; y < clFields.length; y++)
			{
				if (clFields[y].getName().compareTo(columnName) == 0)
				{
					try
					{
						switch(columnType)
						{
						case Types.INTEGER:
						case Types.BIGINT:
						case Types.SMALLINT:
						case Types.TINYINT:
						case Types.NUMERIC:
						case Types.BIT:
							if (clFields[y].getType().getName().compareTo("long") == 0)
								retSql += sSep + columnName + " = " + clFields[y].getLong(tbObj);
							else
								retSql += sSep + columnName + " = " + clFields[y].getInt(tbObj);
							sSep = ", ";
							break;
							
						case Types.DATE:
						case Types.TIMESTAMP:
							String dataFmt = "";
							try {
								Class[] signature = new Class[1];
								Class cObj = Class.forName(tbObj.getClass().getName());
								signature[0] = Class.forName(clFields[y].getType().getName());
								Method mtGet = cObj.getMethod("get" + clFields[y].getName().substring(0, 1).toUpperCase() + 
															  clFields[y].getName().substring(1) + "_fmt",(Class[]) null);
								dataFmt = (String)mtGet.invoke(tbObj, (Object[]) null);
							}
							catch(Exception E)
							{
								dataFmt = "yyyy-MM-dd HH:mm:ss";
							}
	
							DateFormat df = new SimpleDateFormat(dataFmt);
							
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + df.format(clFields[y].get(tbObj)) + "'";
							}
							sSep = ", ";
							break;
	
						case Types.BLOB:
						case Types.CHAR:
						case Types.VARCHAR:
						case Types.LONGVARCHAR:
							// log.debug("setting '" + clFields[y].getName() +"' to '" + clFields[y].get(tbObj) + "'");
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + quoteString(String.valueOf(clFields[y].get(tbObj))) + "'";
							}
							sSep = ", ";
							break;
						
						case Types.FLOAT:
						case Types.REAL:
						case Types.DOUBLE:
						case Types.DECIMAL:
							try
							{
								retSql += sSep + columnName + " = " + clFields[y].getDouble(tbObj);
								sSep = ", ";
							}
							catch(Exception e)
							{
								e.printStackTrace();
							}
							break;
							
						default:
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + quoteString(String.valueOf(clFields[y].get(tbObj))) + "'";
							}
							sSep = ", ";
						}
					}
					catch(Exception e)
					{
			    		retVal = "Error " + e.getStackTrace().getClass().getSimpleName() + 
			    				 " (" + e.getMessage() + ") retrieving fields value from object '";
						throw new RMException(retVal);
					}
					// No need to proceed further looking for other columns
					y = clFields.length + 1;
				}						
			}
		}
		return retSql;
	}
	
	// Get a where clause based on the specified columns
	// Date fields should have a method 'getAttributeName_fmt()' otherwise it will default to 'yyyy-MM-dd HH:mm:ss' 
	private String getWhereClauseOnId(String onColumns) throws RMException
	{
		String whereClause = "WHERE ";
		String sep = "";
    	Field[] clFields = null;

    	clFields = getAllFields(this.getClass());
		try 
		{
	    	for(String fieldName: onColumns.split(";"))
	    	{
				for(int y = 0; y < clFields.length; y++)
				{
					if (clFields[y].getName().compareTo(fieldName) == 0)
					{
						if (clFields[y].getType().getName().compareTo("int") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getInt(this);
						}
						else if (clFields[y].getType().getName().compareTo("long") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getLong(this);
						}
						else if (clFields[y].getType().getName().compareTo("float") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getFloat(this);
						}
						else if (clFields[y].getType().getName().compareTo("double") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getDouble(this);
						}
						else if (clFields[y].getType().getName().compareTo("char") == 0)
						{
							whereClause += sep + fieldName + " = '" + clFields[y].getChar(this) + "'";
						}
						else if (clFields[y].getType().getName().compareTo("java.lang.String") == 0)
						{
							whereClause += sep + fieldName + " = '" + clFields[y].get(this) + "'";
						}
						else if (clFields[y].getType().getName().compareTo("java.util.Date") == 0)
						{
							String dataFmt = "";
							try 
							{
								Class<?>[] signature = new Class[1];
								Class<?> cObj = Class.forName(this.getClass().getName());
								signature[0] = Class.forName(clFields[y].getType().getName());
								Method mtGet = cObj.getMethod("get" + clFields[y].getName().substring(0, 1).toUpperCase() + 
															  clFields[y].getName().substring(1) + "_fmt",(Class[]) null);
								dataFmt = (String)mtGet.invoke(this, (Object[]) null);
							}
							catch(Exception e)
							{
								dataFmt = "yyyy-MM-dd HH:mm:ss";
							}
	
							DateFormat df = new SimpleDateFormat(dataFmt);
							if (clFields[y].get(this) != null)
							{
								whereClause += sep + fieldName + " = '" + df.format(clFields[y].get(this)) + "'";
							}
						}
						
						if (whereClause.compareTo("WHERE ") != 0)
						{
							sep = " AND ";
						}
						break;
					}
				}
	    	}
		}
		catch (Exception e) 
		{
			throw new RMException(e);
		}
		return(whereClause);
	}
	
    private static void populateObjectAttributesFromRecordset(Object objInst, ResultSetMetaData rsm, ResultSet rs) 
			throws RMException
	{
		Exception e = null;
		Field[] clFields = getAllFields(objInst.getClass());
		try
		{
			for(int i = 1; i <= rsm.getColumnCount(); i++)
			{
				for(int y = 0; y < clFields.length; y++)
				{
					if (clFields[y].getName().compareTo(rsm.getColumnName(i)) == 0)
					{
						switch(rsm.getColumnType(i))
						{
						case Types.INTEGER:
						case Types.BIGINT:
						case Types.SMALLINT:
						case Types.TINYINT:
						case Types.NUMERIC:
						case Types.BIT:
							clFields[y].setInt(objInst, rs.getInt(clFields[y].getName()));
							break;
							
						case Types.DATE:
							try
							{
								clFields[y].set(objInst, rs.getDate(clFields[y].getName()));
							}
							catch(SQLException e1)
							{
								clFields[y].set(objInst, null);
							}
							break;

						case Types.TIMESTAMP:
							try
							{
								clFields[y].set(objInst, rs.getTimestamp(clFields[y].getName()));
							}
							catch(SQLException e1)
							{
								clFields[y].set(objInst, null);
							}
							break;

						case Types.BLOB:
						case Types.CHAR:
						case Types.VARCHAR:
						case Types.LONGVARCHAR:
							clFields[y].set(objInst, rs.getString(clFields[y].getName()));
							break;
						
						case Types.FLOAT:
						case Types.REAL:
						case Types.DOUBLE:
						case Types.DECIMAL:
							clFields[y].set(objInst, rs.getDouble(clFields[y].getName()));
							break;
							
						default:
							clFields[y].set(objInst, rs.getString(clFields[y].getName()));
							
						}							
						y = clFields.length + 1;
					}
				}
			}
		}
		catch (IllegalArgumentException e1) {
			e = e1;
		}
		catch (IllegalAccessException e1) {
			e = e1;
		}
		catch (SQLException e1) {
			e = e1;
		}
		if (e != null)
		{
			throw new RMException(e);
		}
	}
	
	public void populateObject(String sql, Object tbObj) throws RMException
	{
		conn = DBConnection.getInstance();
		logger.trace("Querying database '" + sql + "'");
		conn.executeQuery(sql);
		ResultSet rs = conn.getRs();
		try 
		{
			if (rs.next())
		    	populateObjectAttributesFromRecordset(tbObj, conn.getRsm(), rs);
			else
				throw new RMException("No record found", RMException.ERR_NO_RECORD_FOUND);	
		}
		catch (SQLException e) 
		{
			logger.error("Got exception '" + e.getMessage()+ "'");
			logger.error(Utility.stacktraceToString(e));
			throw new RMException(e);
		}
	}

    /*
     * It works only for numeric id... the sql string will be invalid otherwise
     */
    public void populateObject(String idName) throws RMException
	{
    	Exception e = null;
		String sql = "SELECT * FROM " + tableName + " WHERE " + idName + " = ";
		try {
			sql += this.getClass().getDeclaredField(idName).get(this);
		}
		catch (IllegalArgumentException e1) {
			e = e1;
		}
		catch (SecurityException e1) {
			e = e1;
		}
		catch (IllegalAccessException e1) {
			e = e1;
		}
		catch (NoSuchFieldException e1) {
			e = e1;
		}
		if (e != null)
		{
			throw new RMException(e);
		}
		populateObject(sql, this);
	}
	
	public static ArrayList<?> populateCollection(String sql, Class<?> objClass) throws RMException
	{
    	String retVal = null;
    	ArrayList<Object> aList = new ArrayList<Object>();
    	
		conn = DBConnection.getInstance();
    	conn.executeQuery(sql);
    	
		ResultSet rs = conn.getRs();
		ResultSetMetaData rsm = conn.getRsm();
		try
		{
			while(rs.next())
			{
				Object objInst = objClass.newInstance();
				populateObjectAttributesFromRecordset(objInst, rsm, rs);
				aList.add(objInst);
			}
		}
		catch (Exception e) 
		{
    		retVal = "Error " + e.getStackTrace().getClass().getSimpleName() + " (" + e.getMessage() + ") retrieving fields from class '" + 
					  objClass.getName() + "'";
			throw new RMException(retVal);
		}
		return aList;
	}

	public ArrayList<?> populateCollectionOnCondition(String whereClause, Class<?> objClass) throws RMException
	{
		return(populateCollection("SELECT * FROM " + tableName + " " + whereClause, objClass));
	}
	
	protected ArrayList<String[]> getObjectChanges(Object oldInst, Object newInst) throws RMException
	{
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		ArrayList<String[]> changes = new ArrayList<String[]>();
		try
		{
	    	for (Field f : getAllFields(oldInst.getClass())) 
			{
				String[] locChanges = null;
	    		if (f.getType() == int.class)
	    		{
	    			if (f.getInt(oldInst) != f.getInt(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Integer.toString(f.getInt(oldInst)));
	    				locChanges[2] = "" + f.getInt(newInst);
	    			}
	    		}
	    		else if (f.getType() == long.class)
	    		{
	    			if (f.getLong(oldInst) != f.getLong(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Long.toString(f.getLong(oldInst)));
	    				locChanges[2] = "" + f.getLong(newInst);
	    			}
	    		}						
	    		else if (f.getType() == boolean.class)
	    		{
	    			if (f.getBoolean(oldInst) != f.getBoolean(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Boolean.toString(f.getBoolean(oldInst)));
	    				locChanges[2] = "" + f.getBoolean(newInst);
	    			}
	    		}						
	    		else if (f.getType() == String.class)
	    		{
	    			Object o = f.get(oldInst);
	    			Object n = f.get(newInst);
	    			if (!((o == null) && (n == null)) &&
	    				(((o == null ) && (n != null)) || ((o != null) && (n == null)) ||
	    				 ((String) f.get(oldInst)).compareTo((String) f.get(newInst)) != 0))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (String) (oldInst == null ? "" : f.get(oldInst));
	    				locChanges[2] = (String) f.get(newInst);
	    			}
	    		}			
	    		else if (f.getType() == Date.class)
	    		{
	    			Object o = f.get(oldInst);
	    			Object n = f.get(newInst);
	    			if (!((o == null) && (n == null)) &&
	    				(((o == null ) && (n != null)) || ((o != null) && (n == null)) ||
	    				 !((Date) f.get(oldInst)).equals((Date) f.get(newInst))))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : formatter.format(f.get(oldInst)));
	    				locChanges[2] = (newInst == null ? "" : formatter.format(f.get(newInst)));
					}
				}
	    		
	    		if (locChanges != null)
	    		{
	    			changes.add(locChanges);
	    		}
			}
		}
		catch(Exception e)
		{
			throw new RMException(e);
		}
		return(changes);
	}
	
	
	public void update(String avoidColumns, String whereClause) throws RMException
	{
    	String sql = null;

    	/*
    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
    	 */
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		sql = "UPDATE " + tableName + " SET ";
		sql += this.getUpdateStatement(sqlQueryColNames, this, avoidColumns);
		sql += " " + whereClause;
		conn = DBConnection.getInstance();
		conn.executeQuery(sql);
    }

	public void update(String idColumns) throws RMException
	{
		update(idColumns, getWhereClauseOnId(idColumns));
    }

	public static void updateCollection(ArrayList<?> collection, String idColName, Class<?> objectClass) 
			throws RMException
	{
		if (collection.size() == 0)
			return;
    	String sql = "";
		String tableName = ((DBInterface) collection.get(0)).tableName;
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		conn = DBConnection.getInstance();
    	for(int i = 0; i < collection.size(); i++)
    	{
	    	/*
	    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
	    	 */
			sql = "UPDATE " + tableName + " SET ";
			sql += (((DBInterface) collection.get(i))).getUpdateStatement(sqlQueryColNames, collection.get(i), idColName);
			// Get the id of the current object 
			Class<?> c = collection.get(i).getClass();
			Method m;
			try 
			{
				m = c.getMethod("get" + idColName.substring(0,1).toUpperCase() + idColName.substring(1), new Class[] {});
				sql += " WHERE " + idColName + " = " + ((Integer) m.invoke(collection.get(i))).intValue();
			}
			catch (Exception e) 
			{
				throw new RMException(e);
			}
			conn.executeQuery(sql);
    	}
	}

	public void insert(String idColName, Object objectToInsert, int id) throws RMException
	{
    	String sql = "";
    	/*
    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
    	 */
		String sqlQueryColNames = "SELECT * FROM " + ((DBInterface) objectToInsert).tableName + " WHERE 1 = 0";	    	
		sql = "INSERT INTO " + ((DBInterface) objectToInsert).tableName + " SET ";
		sql += this.getUpdateStatement(sqlQueryColNames, objectToInsert, idColName);
		conn = DBConnection.getInstance();
		conn.executeQuery(sql);
		ResultSet rs = null;
		conn.executeQuery("SELECT LAST_INSERT_ID() AS id");
		try 
		{
			rs = conn.getRs();
			if (rs.next())
			{
				id = rs.getInt("id");
			}
			rs.close();
		}
		catch (Exception e) 
		{
			throw new RMException(e);
		}
	}
	public void insert(String idColName, Object objectToInsert) throws RMException
	{
    	String sql = "";
    	/*
    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
    	 */
		String sqlQueryColNames = "SELECT * FROM " + ((DBInterface) objectToInsert).tableName + " WHERE 1 = 0";	    	
		sql = "INSERT INTO " + ((DBInterface) objectToInsert).tableName + " SET ";
		sql += this.getUpdateStatement(sqlQueryColNames, objectToInsert, idColName);
		conn = DBConnection.getInstance();
		conn.executeQuery(sql);
	}

	public static void insertCollection(ArrayList<?> collection, String idColName, Class<?> objectClass) 
			throws RMException
	{
		if (collection.size() == 0)
			return;
    	String sql = "";
		String tableName = ((DBInterface) collection.get(0)).tableName;
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		conn = DBConnection.getInstance();
    	for(int i = 0; i < collection.size(); i++)
    	{
	    	/*
	    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
	    	 */
			sql = "INSERT INTO " + tableName + " SET ";
			sql += (((DBInterface) collection.get(i))).getUpdateStatement(sqlQueryColNames, collection.get(i), idColName);
			conn.executeQuery(sql);
    	}
	}

	public void delete(String sql) throws RMException
	{
		conn = DBConnection.getInstance();
		conn.executeQuery(sql);
	}
		
	public static void executeStatement(String sql, boolean inTransaction) 
			throws RMException 
	{
		conn = DBConnection.getInstance();
    	if (inTransaction)
    		TransactionStart();
    	try
    	{
    		conn.executeQuery(sql);
		}
		catch (RMException e) 
		{
			if (inTransaction)
			{
				TransactionRollback();
			}
			throw e;
		} 
    	
    	if (inTransaction)
    		TransactionCommit();
	}
	
	public static void TransactionStart() throws RMException
	{
		conn = DBConnection.getInstance();
		conn.executeQuery("START TRANSACTION");
	}

	public static void TransactionCommit() throws RMException 
	{
		conn = DBConnection.getInstance();
		conn.executeQuery("COMMIT");
	}

	public static void TransactionRollback() 
	{
		try 
		{
			conn = DBConnection.getInstance();
			conn.executeQuery("ROLLBACK");
		}
		catch (RMException e)
		{
			;
		}
	}
	
}