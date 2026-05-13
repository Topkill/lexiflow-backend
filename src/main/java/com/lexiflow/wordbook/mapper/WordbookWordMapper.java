package com.lexiflow.wordbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.wordbook.domain.WordbookWord;
import com.lexiflow.wordbook.dto.WordbookWordPickRow;
import com.lexiflow.wordbook.dto.WordbookWordRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WordbookWordMapper extends BaseMapper<WordbookWord> {

    IPage<WordbookWordRow> selectWordPage(
            Page<WordbookWordRow> page,
            @Param("wordbookId") Long wordbookId,
            @Param("keyword") String keyword
    );

    List<WordbookWordPickRow> selectNewWordCandidates(
            @Param("wordbookId") Long wordbookId,
            @Param("startSequenceNo") Integer startSequenceNo,
            @Param("limit") Integer limit
    );
}
