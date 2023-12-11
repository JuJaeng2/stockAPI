package com.dayone.service;

import com.dayone.exception.impl.NoCompanyException;
import com.dayone.model.Company;
import com.dayone.model.ScrapedResult;
import com.dayone.persist.CompanyRepository;
import com.dayone.persist.DividendRepository;
import com.dayone.persist.entity.CompanyEntity;
import com.dayone.persist.entity.DividendEntity;
import com.dayone.scraper.Scraper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.Trie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CompanyService {

    private final Trie trie;
    private final CompanyRepository companyRepository;
    private final Scraper scraper;
    private final DividendRepository dividendRepository;

    public Company save(String ticker) {
        boolean exist = this.companyRepository.existsByTicker(ticker);
        if (exist){
            throw new RuntimeException("Already exists ticer");
        }

        return this.storeCompanyAndDividend(ticker);
    }

    public Page<CompanyEntity> getAllCompany(Pageable pageable) {
        return companyRepository.findAll(pageable);
    }

    private Company storeCompanyAndDividend(String ticker) {
        // 1. ticker 를 기준으로 회사를 스크래핑
        Company company = this.scraper.scrapCompanyByTicker(ticker);
        if (ObjectUtils.isEmpty(company)) {
            throw new RuntimeException("failed to scrap ticker => " + ticker);
        }

        // 2. 해당 회사가 존재할 경우, 회사의 배당금 정보를 스크래핑
        ScrapedResult scrapedResult = this.scraper.scrap(company);

        // 3. 스크래핑 결과 반환
        CompanyEntity companyEntity = this.companyRepository.save(new CompanyEntity(company));
        List<DividendEntity> dividendEntityList= scrapedResult.getDividends().stream()
                .map(dividend -> new DividendEntity(companyEntity.getId(), dividend))
                .collect(Collectors.toList());
        this.dividendRepository.saveAll(dividendEntityList);
        return company;
    }

    public List<String> getCompanyNamesByKeyword(String keyword) {
        Pageable limit = PageRequest.of(0,10);
        Page<CompanyEntity> companyEntities = this.companyRepository.findByNameStartingWithIgnoreCase(keyword, limit);
        List<String> nameList = companyEntities.stream()
                .map(companyEntity -> companyEntity.getName())
                .collect(Collectors.toList());
        return nameList;
    }

    public void addAutocompleteKeyword(String keyword) {
        this.trie.put(keyword, null);
    }

    public List<String> autocomplete(String keyword) {
        return (List<String>) this.trie.prefixMap(keyword).keySet()
                .stream()
                .collect(Collectors.toList());
    }

    public void deleteAutocompleteKeyword(String keyword) {
        this.trie.remove(keyword);
    }

    public String deleteCompany(String ticker) {
        // 1. 배당금 정보 삭제
        CompanyEntity companyEntity = this.companyRepository.findByTicker(ticker)
                .orElseThrow(() -> new NoCompanyException());
        this.dividendRepository.deleteAllByCompanyId(companyEntity.getId());
        // 2. 회사 정보 삭제
        this.companyRepository.deleteById(companyEntity.getId());
        return companyEntity.getName();
    }
}
