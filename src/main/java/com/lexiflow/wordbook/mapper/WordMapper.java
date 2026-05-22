package com.lexiflow.wordbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.dto.AdminWordRow;
import com.lexiflow.wordbook.dto.LexiflowDictionaryEntryRow;
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

    WordRow selectLookupWord(
            @Param("wordbookId") Long wordbookId,
            @Param("normalizedText") String normalizedText,
            @Param("compactText") String compactText
    );

    WordRow selectLookupWordInEnabledWordbooks(
            @Param("normalizedText") String normalizedText,
            @Param("compactText") String compactText
    );

    LexiflowDictionaryEntryRow selectLookupDictionaryEntry(
            @Param("normalizedText") String normalizedText,
            @Param("compactText") String compactText
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

    List<Word> selectChoiceQuestionCandidates(@Param("wordbookId") Long wordbookId);

    @Delete("DELETE FROM word WHERE wordbook_id = #{wordbookId}")
    int physicalDeleteByWordbookId(@Param("wordbookId") Long wordbookId);
}
