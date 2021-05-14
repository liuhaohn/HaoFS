package com.hao.server.mapper;

import com.hao.server.model.Node;
import com.hao.server.model.*;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import java.util.*;

@Mapper
public interface NodeMapper {
	/**
	 * 
	 * <h2>根据文件夹ID查询其中的所有文件节点</h2>
	 * <p>
	 * 该方法用于一次性将目标文件夹下的全部文件节点查询出来，如果超过限值，则只查询限值内的节点数量。
	 * </p>
	 * 
	 * @param pfid
	 *            java.lang.String 目标文件夹ID
	 * @return java.util.List 文件节点列表
	 */
	@Select("SELECT * FROM FILE WHERE file_parent_folder = #{pfid,jdbcType=VARCHAR} LIMIT 0,2147483647")
	@Results(id = "nodeResultMap", value = {
			@Result(id = true, column = "file_id", jdbcType = JdbcType.VARCHAR, property = "fileId"),
			@Result(column = "file_name", jdbcType=JdbcType.VARCHAR, property = "fileName"),
			@Result(column = "file_size", jdbcType=JdbcType.VARCHAR, property = "fileSize"),
			@Result(column = "file_parent_folder", jdbcType=JdbcType.VARCHAR, property = "fileParentFolder"),
			@Result(column = "file_creation_date", jdbcType=JdbcType.VARCHAR, property = "fileCreationDate"),
			@Result(column = "file_creator", jdbcType=JdbcType.VARCHAR, property = "fileCreator"),
			@Result(column = "file_path", jdbcType=JdbcType.VARCHAR, property = "filePath"),
	})
	List<Node> queryByParentFolderId(final String pfid);

	/**
	 * 
	 * <h2>按照父文件夹的ID查找其下的所有文件（分页）</h2>
	 * <p>
	 * 该方法需要传入一个Map作为查询条件，其中需要包含
	 * pfid（父文件夹的ID），
	 * offset（起始偏移），
	 * rows（查询行数）。
	 * </p>
	 * 
	 * @param keyMap
	 *            java.util.Map 封装查询条件的Map对象
	 * @return java.util.List 查询结果
	 */
	@Select("SELECT * FROM FILE WHERE file_parent_folder = #{pfid,jdbcType=VARCHAR} LIMIT #{offset,jdbcType=INTEGER},#{rows,jdbcType=INTEGER}")
	@ResultMap("nodeResultMap")
	List<Node> queryByParentFolderIdSection(final Map<String, Object> keyMap);

	/**
	 * 
	 * <h2>按照父文件夹的ID统计其下的所有文件数目</h2>
	 * <p>
	 * 该方法主要用于配合queryByParentFolderIdSection方法实现分页加载。
	 * </p>
	 * 
	 * @param pfid
	 *            java.lang.String 父文件夹ID
	 * @return long 文件总数
	 */
	@Select("SELECT COUNT(file_id) FROM FILE WHERE file_parent_folder = #{pfid,jdbcType=VARCHAR}")
	long countByParentFolderId(final String pfid);

	@Insert("INSERT INTO FILE VALUES (#{fileId,jdbcType=VARCHAR},#{fileName,jdbcType=VARCHAR}," +
			"#{fileSize,jdbcType=VARCHAR},#{fileParentFolder,jdbcType=VARCHAR}," +
			"#{fileCreationDate,jdbcType=VARCHAR},#{fileCreator,jdbcType=VARCHAR}," +
			"#{filePath,jdbcType=VARCHAR})")
	int insert(final Node f);

	@Update("UPDATE FILE SET file_name = " +
			"#{fileName,jdbcType=VARCHAR},file_size = " +
			"#{fileSize,jdbcType=VARCHAR},file_parent_folder = " +
			"#{fileParentFolder,jdbcType=VARCHAR},file_creation_date = " +
			"#{fileCreationDate,jdbcType=VARCHAR},file_creator = " +
			"#{fileCreator,jdbcType=VARCHAR},file_path = " +
			"#{filePath,jdbcType=VARCHAR} WHERE file_id = " +
			"#{fileId,jdbcType=VARCHAR}")
	int update(final Node f);

	@Delete("DELETE FROM FILE WHERE file_parent_folder = #{pfid,jdbcType=VARCHAR}")
	int deleteByParentFolderId(final String pfid);

	@Delete("DELETE FROM FILE WHERE file_id = #{fileId,jdbcType=VARCHAR}")
	int deleteById(final String fileId);

	@Select("SELECT * FROM FILE WHERE file_id = #{fileId,jdbcType=VARCHAR}")
	@ResultMap("nodeResultMap")
	Node queryById(final String fileId);

	@Update("UPDATE FILE SET" +
			" file_name = #{newFileName,jdbcType=VARCHAR} WHERE file_id =" +
			" #{fileId,jdbcType=VARCHAR}")
	int updateFileNameById(final Map<String, String> map);

	/**
	 * 
	 * <h2>根据文件块DI查询对所有对应的节点</h2>
	 * <p>
	 * 该方法用于查询某个文件块ID所对应的所有节点副本，如果超过限值，则只查询限值内的节点数量。
	 * </p>
	 * 
	 * @param path
	 *            java.lang.String 目标文件块ID
	 * @return java.util.List 文件节点列表
	 */
	@Select("SELECT * FROM FILE WHERE file_path = " +
			" #{path,jdbcType=VARCHAR} LIMIT 0,2147483647")
	@ResultMap("nodeResultMap")
	List<Node> queryByPath(final String path);

	/**
	 * 
	 * <h2>根据文件块DI查询对所有对应的节点，并排除指定节点</h2>
	 * <p>
	 * 该方法用于查询某个文件块ID所对应的所有节点副本，结果中不会包括指定ID的节点，如果超过限值，则只查询限值内的节点数量。
	 * </p>
	 * 
	 * @param map
	 *            java.util.Map 其中必须包含：path 目标文件块ID，fileId 要排除的文件节点ID
	 * @return java.util.List 文件节点列表
	 */
	@Select("SELECT * FROM FILE WHERE file_path = " +
			" #{path,jdbcType=VARCHAR} AND file_id <> " +
			" #{fileId,jdbcType=VARCHAR} LIMIT 0,2147483647")
	@ResultMap("nodeResultMap")
	List<Node> queryByPathExcludeById(final Map<String, String> map);

	/**
	 * 
	 * <h2>查询与目标文件节点处于同一文件夹下的全部文件节点</h2>
	 * <p>
	 * 该方法用于一次性将与目标文件同文件夹的文件节点查询出来，如果超过限值，则只查询限值内的节点数量。
	 * </p>
	 * 
	 * @param fileId
	 *            java.lang.String 目标文件ID
	 * @return java.util.List 文件节点列表
	 */
	@Select("SELECT * FROM FILE WHERE file_parent_folder in (SELECT " +
			" file_parent_folder " +
			" FROM FILE WHERE file_id = " +
			" #{fileId,jdbcType=VARCHAR}) LIMIT 0,2147483647")
	@ResultMap("nodeResultMap")
	List<Node> queryBySomeFolder(final String fileId);

}
