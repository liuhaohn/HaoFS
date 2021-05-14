package com.hao.server.mapper;

import com.hao.server.model.Folder;
import com.hao.server.model.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.*;

public interface FolderMapper
{
    @Select("SELECT * FROM FOLDER WHERE folder_id = " +
            " #{fid,jdbcType=VARCHAR}")
    @Results(id = "folderResultMap", value = {
            @Result(id = true, column = "folder_id", jdbcType = JdbcType.VARCHAR, property = "folderId"),
            @Result(column = "folder_name", jdbcType = JdbcType.VARCHAR, property = "folderName"),
            @Result(column = "folder_creation_date", jdbcType = JdbcType.VARCHAR, property = "folderCreationDate"),
            @Result(column = "folder_creator", jdbcType = JdbcType.VARCHAR, property = "folderCreator"),
            @Result(column = "folder_parent", jdbcType = JdbcType.VARCHAR, property = "folderParent"),
            @Result(column = "folder_constraint", jdbcType = JdbcType.VARCHAR, property = "folderConstraint"),

    })
    Folder queryById(final String fid);

    /**
     *
     * <h2>根据目标文件夹ID查询其中的所有文件夹</h2>
     * <p>该方法用于将某个文件夹下的所有文件夹全部查询出，并以列表的形式返回。如果结果数量超出了最大限额，则只查询限额内的结果。</p>
     * @param pid java.lang.String 目标文件夹ID
     * @return java.util.List 文件夹数组
     */
    @Select("SELECT * FROM FOLDER WHERE folder_parent = " +
            " #{pid,jdbcType=VARCHAR} LIMIT 0,2147483647")
    @ResultMap("folderResultMap")
    List<Folder> queryByParentId(final String pid);

    /**
     *
     * <h2>按照父文件夹的ID统计其下的所有文件夹数目</h2>
     * <p>该方法主要用于配合queryByParentIdSection方法实现分页加载。</p>
     * @param pfid java.lang.String 父文件夹ID
     * @return long 文件夹总数
     */
    @Select("SELECT COUNT(folder_id) FROM FOLDER WHERE folder_parent = " +
            " #{pid,jdbcType=VARCHAR}")
    long countByParentId(final String pid);

    /**
     *
     * <h2>按照父文件夹的ID查找其下的所有文件夹（分页）</h2>
     * <p>该方法需要传入一个Map作为查询条件，其中需要包含pid（父文件夹的ID），offset（起始偏移），rows（查询行数）。</p>
     * @param keyMap java.util.Map 封装查询条件的Map对象
     * @return java.util.List 查询结果
     */
    @Select("SELECT * FROM FOLDER WHERE folder_parent = " +
            " #{pid,jdbcType=VARCHAR} LIMIT " +
            " #{offset,jdbcType=INTEGER},#{rows,jdbcType=INTEGER}")
    @ResultMap("folderResultMap")
    List<Folder> queryByParentIdSection(final Map<String, Object> keyMap);

    @Select("SELECT * FROM FOLDER WHERE folder_parent = " +
            " #{parentId,jdbcType=VARCHAR} AND " +
            " folder_name = " +
            " #{folderName,jdbcType=VARCHAR}")
    @ResultMap("folderResultMap")
    Folder queryByParentIdAndFolderName(final Map<String, String> map);

    @Insert("INSERT INTO FOLDER " +
            " VALUES(#{folderId,jdbcType=VARCHAR},#{folderName,jdbcType=VARCHAR},#{folderCreationDate,jdbcType=VARCHAR},#{folderCreator,jdbcType=VARCHAR},#{folderParent,jdbcType=VARCHAR},#{folderConstraint,jdbcType=INTEGER});")
    int insertNewFolder(final Folder f);

    @Delete("DELETE FROM FOLDER WHERE " +
            " folder_id=#{folderId,jdbcType=VARCHAR}")
    int deleteById(final String folderId);

    @Update("UPDATE FOLDER " +
            " SET folder_name = #{newName,jdbcType=VARCHAR} WHERE folder_id " +
            " = #{folderId,jdbcType=VARCHAR}")
    int updateFolderNameById(final Map<String, String> map);

    @Update("UPDATE " +
            " FOLDER SET folder_constraint = #{newConstraint,jdbcType=INTEGER} WHERE " +
            " folder_id = #{folderId,jdbcType=VARCHAR}")
    int updateFolderConstraintById(final Map<String, Object> map);

    @Update("UPDATE FOLDER SET " +
            " folder_parent = #{locationpath,jdbcType=VARCHAR} WHERE folder_id = " +
            " #{folderId,jdbcType=VARCHAR}")
	int moveById(Map<String, String> map);
	
	/**
	 * 
	 * <h2>将指定文件夹节点（按照ID确定）更新</h2>
	 * <p>该方法将会按照ID找到对应的文件夹条目，并更新除文件夹ID以外的全部属性。</p>
	 * @param f Folder 更新的文件夹，必须完整非空
	 * @return int 影响条目
	 */
	@Update("UPDATE FOLDER SET folder_name = " +
            " #{folderName,jdbcType=VARCHAR},folder_creation_date = " +
            " #{folderCreationDate,jdbcType=VARCHAR},folder_creator = " +
            " #{folderCreator,jdbcType=VARCHAR},folder_parent = " +
            " #{folderParent,jdbcType=VARCHAR},folder_constraint = " +
            " #{folderConstraint,jdbcType=INTEGER} WHERE folder_id = " +
            " #{folderId,jdbcType=VARCHAR}")
	int update(final Folder f);
}
