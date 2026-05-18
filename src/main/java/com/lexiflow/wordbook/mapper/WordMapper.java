package com.lexiflow.wordbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.dto.AdminWordRow;
import com.lexiflow.wordbook.dto.WordPickRow;
import com.lexiflow.wordbook.dto.WordRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface WordMapper extends BaseMapper<Word> {

    IPage<WordRow> selectWordPage(
            Page<WordRow> page,
            @Param("wordbookId") Long wordbookId,
            @Param("keyword") String keyword
    );

    AdminWordRow selectAdminWord(
            @Param("wordbookId") Long wordbookId,
            @Param("wordId") Long wordId
    );

    IPage<AdminWordRow> selectAdminWordPage(
            Page<AdminWordRow> page,
            @Param("wordbookId") Long wordbookId,
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled
    );

    List<WordPickRow> selectNewWordCandidates(
            @Param("wordbookId") Long wordbookId,
            @Param("startSequenceNo") Integer startSequenceNo,
            @Param("limit") Integer limit
    );

    @Delete("DELETE FROM word WHERE wordbook_id = #{wordbookId}")
    int physicalDeleteByWordbookId(@Param("wordbookId") Long wordbookId);
}
