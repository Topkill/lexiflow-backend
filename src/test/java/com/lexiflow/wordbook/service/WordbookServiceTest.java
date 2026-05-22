package com.lexiflow.wordbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.LexiflowDictionaryEntryRow;
import com.lexiflow.wordbook.dto.WordResponse;
import com.lexiflow.wordbook.dto.WordRow;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WordbookServiceTest {

    @Mock
    private WordbookMapper wordbookMapper;

    @Mock
    private WordMapper wordMapper;

    private WordbookService service;

    @BeforeEach
    void setUp() {
        service = new WordbookService(wordbookMapper, wordMapper, new ObjectMapper());
        when(wordbookMapper.selectOne(any())).thenReturn(wordbook());
    }

    @Test
    void lookupWordShouldPreferCurrentWordbook() {
        WordRow current = wordRow(1L, "known", "known", "知道的");
        when(wordMapper.selectLookupWord(10L, "known", "known")).thenReturn(current);

        WordResponse response = service.lookupWord(10L, "known");

        assertThat(response.id()).isEqualTo("1");
        assertThat(response.word()).isEqualTo("known");
        verify(wordMapper, never()).selectLookupWordInEnabledWordbooks("known", "known");
        verify(wordMapper, never()).selectLookupDictionaryEntry("known", "known");
    }

    @Test
    void lookupWordShouldFallbackToEnabledWordbooks() {
        WordRow global = wordRow(2L, "known", "known", "知道的");
        when(wordMapper.selectLookupWord(10L, "known", "known")).thenReturn(null);
        when(wordMapper.selectLookupWordInEnabledWordbooks("known", "known")).thenReturn(global);

        WordResponse response = service.lookupWord(10L, "known");

        assertThat(response.id()).isEqualTo("2");
        assertThat(response.primaryDefinition()).isEqualTo("知道的");
        verify(wordMapper, never()).selectLookupDictionaryEntry("known", "known");
    }

    @Test
    void lookupWordShouldFallbackToLexiflowDictionary() {
        when(wordMapper.selectLookupWord(10L, "known", "known")).thenReturn(null);
        when(wordMapper.selectLookupWordInEnabledWordbooks("known", "known")).thenReturn(null);
        when(wordMapper.selectLookupDictionaryEntry("known", "known")).thenReturn(new LexiflowDictionaryEntryRow(
                9L,
                "known",
                "known",
                "[\"nəʊn\",\"noʊn\"]",
                "adj.",
                "已知的",
                "[[\"adj.\",[\"已知的\",\"知名的\"],[\"a known fact\",\"一个已知事实\"]]]",
                "简明英汉汉英词典"
        ));

        WordResponse response = service.lookupWord(10L, "known");

        assertThat(response.id()).isEqualTo("dict:9");
        assertThat(response.word()).isEqualTo("known");
        assertThat(response.phonetic0()).isEqualTo("nəʊn");
        assertThat(response.phonetic1()).isEqualTo("noʊn");
        assertThat(response.trans()).contains("已知的", "知名的");
        assertThat(response.sentences()).contains("a known fact", "一个已知事实");
        assertThat(response.tags()).isEqualTo("简明英汉汉英词典");
    }

    private Wordbook wordbook() {
        Wordbook wordbook = new Wordbook();
        wordbook.setId(10L);
        wordbook.setEnabled(true);
        return wordbook;
    }

    private WordRow wordRow(Long id, String word, String normalizedWord, String primaryDefinition) {
        return new WordRow(
                id,
                word,
                normalizedWord,
                null,
                null,
                "[{\"pos\":\"adj.\",\"cn\":\"" + primaryDefinition + "\"}]",
                null,
                null,
                null,
                null,
                null,
                "adj.",
                primaryDefinition,
                null,
                1,
                1,
                1
        );
    }
}
