package com.hao.server.mapper;

import com.hao.server.model.Propertie;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

@Mapper
public interface PropertiesMapper {
	@Insert("INSERT INTO PROPERTIES " +
			" VALUES(#{propertieKey,jdbcType=VARCHAR},#{propertieValue,jdbcType=VARCHAR})")
	int insert(final Propertie p);

	@Delete("DELETE FROM PROPERTIES WHERE " +
			" propertie_key = #{propertieKey,jdbcType=VARCHAR}")
	int deleteByKey(final String propertieKey);

	@Results(id = "propertiesResultMap", value = {
			@Result(id = true, column = "propertie_key", jdbcType = JdbcType.VARCHAR, property = "propertieKey"),
			@Result(column = "propertie_value", jdbcType = JdbcType.VARCHAR, property = "propertieValue")
	})
	@Select("SELECT * FROM PROPERTIES WHERE propertie_key = " +
			" #{propertieKey,jdbcType=VARCHAR}")
	Propertie selectByKey(final String propertieKey);

	@Update("UPDATE PROPERTIES SET propertie_value = " +
			" #{propertieValue,jdbcType=VARCHAR} WHERE propertie_key = " +
			" #{propertieKey,jdbcType=VARCHAR}")
	int update(final Propertie p);
}
