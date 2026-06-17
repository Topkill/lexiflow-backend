package com.lexiflow.admin.wordbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.wordbook.dto.*;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.AdminWordRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import com.lexiflow.wordbook.service.WordDictionaryJsonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminWordbookService {

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final WordDictionaryJsonService wordDictionaryJsonService;
    /**
     * 分页查询词本列表。
     * <p>
     * 根据查询条件筛选词本，并按排序字段、创建时间和ID进行排序。
     * 支持按类型、启用状态和关键词（名称或编码）进行过滤。
     *
     * @param request 查询请求参数，包含分页信息、类型、启用状态和关键词等。
     * @return 分页响应对象，包含词本列表数据、总记录数、当前页码和每页大小。
     */
    public PageResponse<AdminWordbookResponse> pageWordbooks(AdminWordbookQueryRequest request) {
        // 构建查询条件，设置默认排序规则：升序排序字段 -> 降序创建时间 -> 降序ID
        LambdaQueryWrapper<Wordbook> wrapper = new LambdaQueryWrapper<Wordbook>()
                .orderByAsc(Wordbook::getSortOrder)
                .orderByDesc(Wordbook::getCreatedAt)
                .orderByDesc(Wordbook::getId);

        // 根据类型过滤
        if (request.type() != null) {
            wrapper.eq(Wordbook::getType, request.type());
        }

        // 根据启用状态过滤
        if (request.enabled() != null) {
            wrapper.eq(Wordbook::getEnabled, request.enabled());
        }

        // 根据关键词模糊搜索名称或编码
        if (StringUtils.hasText(request.keyword())) {
            String keyword = request.keyword().trim();
            wrapper.and(query -> query.like(Wordbook::getName, keyword).or().like(Wordbook::getCode, keyword));
        }

        // 执行分页查询
        Page<Wordbook> page = wordbookMapper.selectPage(Page.of(request.page(), request.size()), wrapper);

        // 转换结果并返回分页响应
        return PageResponse.of(
                page.getRecords().stream().map(AdminWordbookResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }



    /**
     * 根据词书ID获取词书信息
     *
     * @param wordbookId 词书ID
     * @return 词书响应对象
     */
    public AdminWordbookResponse getWordbook(Long wordbookId) {
        return AdminWordbookResponse.from(getWordbookEntity(wordbookId));
    }




    /**
     * 创建新的单词本
     *
     * @param adminUserId 管理员用户ID，用于设置创建人和更新人
     * @param request     包含单词本信息的请求对象
     * @return 创建成功后的单词本响应对象
     */
    @Transactional
    public AdminWordbookResponse createWordbook(Long adminUserId, AdminWordbookRequest request) {
        // 标准化单词本编码并确保其唯一性
        String code = normalizeCode(request.code());
        ensureWordbookCodeAvailable(code, null);

        // 构建单词本实体并设置初始属性
        Wordbook wordbook = new Wordbook();
        applyWordbookRequest(wordbook, request, code);
        wordbook.setWordCount(0);
        wordbook.setCreatedBy(adminUserId);
        wordbook.setUpdatedBy(adminUserId);
        wordbook.setDeleted(0);

        // 持久化单词本数据
        wordbookMapper.insert(wordbook);

        // 转换并返回响应结果
        return AdminWordbookResponse.from(wordbook);
    }



    /**
     * 更新单词本信息
     *
     * @param adminUserId 管理员用户ID，用于记录更新人
     * @param wordbookId 单词本ID
     * @param request 单词本更新请求参数
     * @return 更新后的单词本响应对象
     */
    @Transactional
    public AdminWordbookResponse updateWordbook(Long adminUserId, Long wordbookId, AdminWordbookRequest request) {
        // 获取并验证单词本实体是否存在
        Wordbook wordbook = getWordbookEntity(wordbookId);

        // 规范化代码并确保其唯一性（排除当前单词本）
        String code = normalizeCode(request.code());
        ensureWordbookCodeAvailable(code, wordbookId);

        // 将请求参数应用到单词本实体
        applyWordbookRequest(wordbook, request, code);

        // 设置更新人ID并执行数据库更新操作
        wordbook.setUpdatedBy(adminUserId);
        wordbookMapper.updateById(wordbook);

        // 构建并返回响应对象
        return AdminWordbookResponse.from(wordbook);
    }



    /**
     * 启用指定的单词本。
     * <p>
     * 该方法会将单词本的状态设置为启用，并记录最后更新的管理员用户ID。
     * 操作在事务中执行，确保数据一致性。
     *
     * @param adminUserId 执行操作的管理员用户ID，用于记录更新人
     * @param wordbookId  需要启用的单词本ID
     */
    @Transactional
    public void enableWordbook(Long adminUserId, Long wordbookId) {
        // 获取单词本实体
        Wordbook wordbook = getWordbookEntity(wordbookId);
        // 设置启用状态并更新操作人信息
        wordbook.setEnabled(true);
        wordbook.setUpdatedBy(adminUserId);
        // 持久化更新到数据库
        wordbookMapper.updateById(wordbook);
    }



    /**
     * 禁用指定的单词本
     *
     * @param adminUserId 执行操作的管理员用户ID
     * @param wordbookId 需要禁用的单词本ID
     */
    @Transactional
    public void disableWordbook(Long adminUserId, Long wordbookId) {
        // 获取单词本实体并更新状态为禁用，记录更新人后持久化到数据库
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setEnabled(false);
        wordbook.setUpdatedBy(adminUserId);
        wordbookMapper.updateById(wordbook);
    }



        /**
     * 分页查询单词本下的单词列表
     *
     * @param wordbookId 单词本ID，用于校验单词本是否存在
     * @param request    查询请求参数，包含关键词、分页信息、启用状态等
     * @return 分页响应结果，包含转换后的单词响应对象列表、总记录数、当前页码和每页大小
     */
    public PageResponse<AdminWordResponse> pageWords(Long wordbookId, AdminWordQueryRequest request) {
        // 校验单词本是否存在
        getWordbookEntity(wordbookId);
        // 处理关键词：去除首尾空格，若为空则设为null
        String keyword = StringUtils.hasText(request.keyword()) ? request.keyword().trim() : null;
        // 构建分页对象
        Page<AdminWordRow> page = Page.of(request.page(), request.size());
        // 执行数据库分页查询
        var result = wordMapper.selectAdminWordPage(page, wordbookId, keyword, request.enabled());
        // 将查询结果转换为响应对象并构建分页响应
        return PageResponse.of(
                result.getRecords().stream().map(this::toWordResponse).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }



    /**
     * 创建新的单词记录。
     * <p>
     * 该方法执行以下主要步骤：
     * 1. 验证词书是否存在。
     * 2. 对单词进行标准化处理，并检查其在当前词书中是否已存在或序列号是否冲突。
     * 3. 构建 Word 实体对象，应用请求参数及作用域设置。
     * 4. 持久化单词数据。
     * 5. 刷新词书的单词计数统计。
     * 6. 返回创建后的单词详细信息。
     * </p>
     *
     * @param adminUserId 管理员用户ID，用于标识创建者
     * @param wordbookId 词书ID，指定单词所属的词书
     * @param request 包含单词详细信息的请求对象
     * @return 包含新创建单词信息的响应对象
     */
    @Transactional
    public AdminWordResponse createWord(Long adminUserId, Long wordbookId, AdminWordRequest request) {
        // 验证词书实体是否存在
        getWordbookEntity(wordbookId);

        // 标准化单词并校验唯一性及序列号可用性
        String normalizedWord = wordDictionaryJsonService.normalizeWord(request.word());
        ensureNormalizedWordAvailable(wordbookId, normalizedWord, null);
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), null);

        // 构建并初始化 Word 实体对象
        Word word = new Word();
        word.setWordbookId(wordbookId);
        applyWordRequest(word, request, normalizedWord, adminUserId);
        applyWordScopeRequest(word, request);
        word.setCreatedBy(adminUserId);
        word.setDeleted(0);

        // 持久化单词数据并更新词书统计信息
        wordMapper.insert(word);
        refreshWordbookCount(wordbookId);

        // 返回创建结果的响应对象
        return getWordResponse(wordbookId, word.getId());
    }



    /**
     * 更新单词信息
     *
     * @param adminUserId 管理员用户ID，用于记录更新人
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     * @param request 包含单词详细信息的请求对象
     * @return 更新后的单词响应对象
     */
    @Transactional
    public AdminWordResponse updateWord(Long adminUserId, Long wordbookId, Long wordId, AdminWordRequest request) {
        // 验证词库是否存在
        getWordbookEntity(wordbookId);
        // 获取并验证单词实体
        Word word = requireWord(wordbookId, wordId);
        // 标准化单词字符串
        String normalizedWord = wordDictionaryJsonService.normalizeWord(request.word());
        // 确保标准化后的单词在当前词库中唯一（排除当前单词自身）
        ensureNormalizedWordAvailable(wordbookId, normalizedWord, wordId);
        // 确保排序号在当前词库中唯一（排除当前单词自身）
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), wordId);
        // 应用单词基础信息及字典相关数据
        applyWordRequest(word, request, normalizedWord, adminUserId);
        // 应用单词作用域相关数据（如排序、难度、启用状态等）
        applyWordScopeRequest(word, request);
        // 执行数据库更新操作
        wordMapper.updateById(word);
        // 刷新词库中的有效单词计数
        refreshWordbookCount(wordbookId);
        return getWordResponse(wordbookId, wordId);
    }


    /**
     * 删除单词
     *
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     */
    @Transactional
    public void removeWord(Long wordbookId, Long wordId) {
        // 验证词库是否存在
        getWordbookEntity(wordbookId);
        // 获取并验证单词实体
        Word word = requireWord(wordbookId, wordId);
        // 执行物理删除
        wordMapper.deleteById(word.getId());
        // 刷新词库中的有效单词计数
        refreshWordbookCount(wordbookId);
    }


    /**
     * 启用单词
     *
     * @param adminUserId 管理员用户ID，用于记录操作人
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     */
    @Transactional
    public void enableWord(Long adminUserId, Long wordbookId, Long wordId) {
        changeWordEnabled(adminUserId, wordbookId, wordId, true);
    }


    /**
     * 禁用单词
     *
     * @param adminUserId 管理员用户ID，用于记录操作人
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     */
    @Transactional
    public void disableWord(Long adminUserId, Long wordbookId, Long wordId) {
        changeWordEnabled(adminUserId, wordbookId, wordId, false);
    }

    /**
     * 获取单词管理响应对象
     *
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     * @return 单词管理响应对象
     * @throws BizException 当单词不存在时抛出异常
     */
    private AdminWordResponse getWordResponse(Long wordbookId, Long wordId) {
        AdminWordRow row = wordMapper.selectAdminWord(wordbookId, wordId);
        if (row == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return toWordResponse(row);
    }

    /**
     * 将数据库行记录转换为单词管理响应对象
     *
     * @param row 数据库查询结果行
     * @return 单词管理响应对象
     */
    private AdminWordResponse toWordResponse(AdminWordRow row) {
        return new AdminWordResponse(
                String.valueOf(row.id()),
                row.word(),
                row.normalizedWord(),
                row.phonetic0(),
                row.phonetic1(),
                row.trans(),
                row.sentences(),
                row.phrases(),
                row.synos(),
                row.relWords(),
                row.etymology(),
                row.primaryPos(),
                row.primaryDefinition(),
                row.tags(),
                row.sequenceNo(),
                row.difficultyLevel(),
                row.examFrequency(),
                row.enabled()
        );
    }

    /**
     * 应用词库请求参数到词库实体
     *
     * @param wordbook 词库实体
     * @param request 词库请求对象
     * @param code 标准化后的词库编码
     */
    private void applyWordbookRequest(Wordbook wordbook, AdminWordbookRequest request, String code) {
        wordbook.setName(request.name().trim());
        wordbook.setCode(code);
        wordbook.setType(request.type());
        wordbook.setDescription(trimToNull(request.description()));
        wordbook.setCoverUrl(trimToNull(request.coverUrl()));
        wordbook.setDifficultyLevel(request.difficultyLevel());
        wordbook.setEnabled(request.enabled());
        wordbook.setSortOrder(request.sortOrder());
    }

    /**
     * 应用单词请求参数到单词实体（基础信息及字典数据）
     *
     * @param word 单词实体
     * @param request 单词请求对象
     * @param normalizedWord 标准化后的单词字符串
     * @param adminUserId 管理员用户ID，用于记录更新人
     */
    private void applyWordRequest(Word word, AdminWordRequest request, String normalizedWord, Long adminUserId) {
        // 标准化并验证必填的释义JSON
        String trans = wordDictionaryJsonService.normalizeRequiredJson(request.trans(), "释义 JSON");
        // 从释义中推导主要词性和主要定义
        WordDictionaryJsonService.WordSummary summary = wordDictionaryJsonService.deriveSummary(trans, request.primaryPos(), request.primaryDefinition());
        word.setWord(request.word().trim());
        word.setNormalizedWord(normalizedWord);
        word.setPhonetic0(trimToNull(request.phonetic0()));
        word.setPhonetic1(trimToNull(request.phonetic1()));
        word.setTrans(trans);
        // 标准化可选的JSON字段
        word.setSentences(wordDictionaryJsonService.normalizeOptionalJson(request.sentences(), "例句 JSON"));
        word.setPhrases(wordDictionaryJsonService.normalizeOptionalJson(request.phrases(), "短语 JSON"));
        word.setSynos(wordDictionaryJsonService.normalizeOptionalJson(request.synos(), "同近义词 JSON"));
        word.setRelWords(wordDictionaryJsonService.normalizeOptionalJson(request.relWords(), "相关词 JSON"));
        word.setEtymology(wordDictionaryJsonService.normalizeOptionalJson(request.etymology(), "词源 JSON"));
        word.setPrimaryPos(summary.primaryPos());
        word.setPrimaryDefinition(summary.primaryDefinition());
        word.setTags(trimToNull(request.tags()));
        word.setUpdatedBy(adminUserId);
    }

    /**
     * 应用单词作用域请求参数到单词实体（排序、难度、考频、启用状态）
     *
     * @param word 单词实体
     * @param request 单词请求对象
     */
    private void applyWordScopeRequest(Word word, AdminWordRequest request) {
        word.setSequenceNo(request.sequenceNo());
        word.setDifficultyLevel(request.difficultyLevel());
        word.setExamFrequency(request.examFrequency());
        word.setEnabled(request.enabled());
    }

    /**
     * 获取词库实体，若不存在则抛出异常
     *
     * @param wordbookId 词库ID
     * @return 词库实体
     * @throws BizException 当词库不存在时抛出异常
     */
    private Wordbook getWordbookEntity(Long wordbookId) {
        Wordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        return wordbook;
    }

    /**
     * 获取单词实体，若不存在则抛出异常
     *
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     * @return 单词实体
     * @throws BizException 当单词不存在时抛出异常
     */
    private Word requireWord(Long wordbookId, Long wordId) {
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getId, wordId)
                .eq(Word::getWordbookId, wordbookId)
                .last("LIMIT 1"));
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }

    /**
     * 修改单词的启用状态
     *
     * @param adminUserId 管理员用户ID，用于记录操作人
     * @param wordbookId 词库ID
     * @param wordId 单词ID
     * @param enabled 是否启用
     */
    private void changeWordEnabled(Long adminUserId, Long wordbookId, Long wordId, boolean enabled) {
        // 验证词库是否存在
        getWordbookEntity(wordbookId);
        // 获取并验证单词实体
        Word word = requireWord(wordbookId, wordId);
        word.setEnabled(enabled);
        word.setUpdatedBy(adminUserId);
        wordMapper.updateById(word);
        // 刷新词库中的有效单词计数
        refreshWordbookCount(wordbookId);
    }

    /**
     * 确保词库编码可用（唯一性检查）
     *
     * @param code 词库编码
     * @param excludedId 排除的词库ID（用于更新场景）
     * @throws BizException 当编码已存在时抛出异常
     */
    private void ensureWordbookCodeAvailable(String code, Long excludedId) {
        LambdaQueryWrapper<Wordbook> wrapper = new LambdaQueryWrapper<Wordbook>().eq(Wordbook::getCode, code);
        if (excludedId != null) {
            wrapper.ne(Wordbook::getId, excludedId);
        }
        if (wordbookMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "词库编码已存在");
        }
    }

    /**
     * 确保标准化单词在词库内可用（唯一性检查）
     *
     * @param wordbookId 词库ID
     * @param normalizedWord 标准化后的单词字符串
     * @param excludedId 排除的单词ID（用于更新场景）
     * @throws BizException 当单词已存在时抛出异常
     */
    private void ensureNormalizedWordAvailable(Long wordbookId, String normalizedWord, Long excludedId) {
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getNormalizedWord, normalizedWord);
        if (excludedId != null) {
            wrapper.ne(Word::getId, excludedId);
        }
        if (wordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "当前词库内单词已存在");
        }
    }

    /**
     * 确保排序号在词库内可用（唯一性检查）
     *
     * @param wordbookId 词库ID
     * @param sequenceNo 排序号
     * @param excludedWordId 排除的单词ID（用于更新场景）
     * @throws BizException 当排序号已存在时抛出异常
     */
    private void ensureSequenceAvailable(Long wordbookId, Integer sequenceNo, Long excludedWordId) {
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getSequenceNo, sequenceNo);
        if (excludedWordId != null) {
            wrapper.ne(Word::getId, excludedWordId);
        }
        if (wordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "词库内顺序号已存在");
        }
    }

    /**
     * 刷新词库中的有效单词计数
     *
     * @param wordbookId 词库ID
     */
    private void refreshWordbookCount(Long wordbookId) {
        // 统计当前词库下已启用的单词数量
        Long count = wordMapper.selectCount(new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getEnabled, true));
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setWordCount(count.intValue());
        wordbookMapper.updateById(wordbook);
    }

    /**
     * 标准化词库编码（去除空格并转为大写）
     *
     * @param code 原始编码
     * @return 标准化后的编码
     */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 去除字符串两端空格，若为空或空白则返回null
     *
     * @param value 原始字符串
     * @return 处理后的字符串或null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
