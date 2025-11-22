package cn.edu.bistu.cs.ir.index;

import cn.edu.bistu.cs.ir.config.Config;
import cn.edu.bistu.cs.ir.model.*;
import cn.edu.bistu.cs.ir.service.SearchCriteria;
import cn.edu.bistu.cs.ir.utils.JsonUtils;
import cn.edu.bistu.cs.ir.utils.StringUtil;
import cn.edu.bistu.cs.ir.utils.PageResponse;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.FileReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 面向<a href="https://lucene.apache.org/">Lucene</a>
 * 索引读、写的服务类
 * @author zhaxijiancuo
 */
@Component
public class IdxService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(IdxService.class);

    private static final Class<? extends Analyzer> DEFAULT_ANALYZER = StandardAnalyzer.class;

    private IndexWriter writer;
    private final Config config;

    public IdxService(@Autowired Config config) throws Exception {
        this.config = config;
        Analyzer analyzer = DEFAULT_ANALYZER.getConstructor().newInstance();
        Directory index;
        try {
            index = FSDirectory.open(Paths.get(config.getIdx()));
            IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
            writer = new IndexWriter(index, writerConfig);
            log.info("索引初始化完成，索引目录为:[{}]", config.getIdx());
        } catch (IOException e) {
            e.printStackTrace();
            log.error("无法初始化索引，请检查提供的索引目录是否可用:[{}]", config.getIdx());
            writer = null;
        }
    }

    public boolean addDocument(String idFld, String id, Document doc){
        if(writer==null||doc==null){
            log.error("Writer对象或文档对象为空，无法添加文档到索引中");
            return false;
        }
        if(StringUtil.isEmpty(idFld)||StringUtil.isEmpty(id)){
            log.error("ID字段名或ID字段值为空，无法添加文档到索引中");
            return false;
        }
        try {
            writer.updateDocument(new Term(idFld, id), doc);
            writer.commit();
            // log.info("成功将ID为[{}]的柔道家信息加入索引", id);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            log.error("构建索引失败");
            return false;
        }
    }

    /**
     * 根据关键词对索引内容进行检索，并将检索结果返回
     * @param kw 待检索的关键词
     * @return 检索得到的文档列表
     */
    public List<Document> queryByKw(String kw) throws Exception{
        //打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = DEFAULT_ANALYZER.getConstructor().newInstance();
        QueryParser parser = new QueryParser("NAME", analyzer);
        Query query = parser.parse(kw);
        TopDocs docs =searcher.search(query, 10);
        ScoreDoc[] hits = docs.scoreDocs;
        List<Document> results = new ArrayList<>();
        for (ScoreDoc doc : hits) {
            results.add(searcher.doc(doc.doc));
        }
        return results;
    }

    /**
     * 根据关键词对索引内容进行分页检索
     * @param kw 待检索的关键词
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果，包含当前页数据和总记录数
     */
    public PageResult queryByKwWithPaging(String kw, int pageNo, int pageSize) throws Exception {
        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;
        
        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = DEFAULT_ANALYZER.getConstructor().newInstance();
        QueryParser parser = new QueryParser("NAME", analyzer);
        Query query = parser.parse(kw);
        
        // 先获取总记录数
        TopDocs totalDocs = searcher.search(query, Integer.MAX_VALUE);
        long total = totalDocs.totalHits.value;
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果
        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(query, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 分页查询结果封装类
     */
    public static class PageResult {
        private final List<Document> documents;
        private final long total;
        
        public PageResult(List<Document> documents, long total) {
            this.documents = documents;
            this.total = total;
        }
        
        public List<Document> getDocuments() {
            return documents;
        }
        
        public long getTotal() {
            return total;
        }
    }

    /**
     * 根据年龄组别进行分页检索
     * @param ageGroup 年龄组别
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByAgeGroup(AgeGroup ageGroup, int pageNo, int pageSize) throws Exception {
        if (ageGroup == null) {
            throw new IllegalArgumentException("年龄组别不能为空");
        }
        
        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;
        
        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // 构建年龄范围查询
        Query query = IntPoint.newRangeQuery("AGE_NUM", ageGroup.getMinAge(), ageGroup.getMaxAge());
        
        // 先获取总记录数
        TopDocs totalDocs = searcher.search(query, Integer.MAX_VALUE);
        long total = totalDocs.totalHits.value;
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果
        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(query, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 根据体重级别进行分页检索
     * @param weightClass 体重级别
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByWeightClass(WeightClass weightClass, int pageNo, int pageSize) throws Exception {
        log.info("IdxService查询体重级别 - weightClass: {}, pageNo: {}, pageSize: {}",
                weightClass != null ? weightClass.getCode() : "null", pageNo, pageSize);

        if (weightClass == null) {
            throw new IllegalArgumentException("体重级别不能为空");
        }

        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;

        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);

        
        // 构建体重级别查询 - 使用QueryParser处理TextField的StandardAnalyzer分词
        String kgCode = weightClass.getCode();
        log.info("构建Lucene查询 - KG: {}, 查询类型: QueryParser+StandardAnalyzer", kgCode);

        // 🎯 修复：KG字段是TextField，需要使用QueryParser来处理StandardAnalyzer分词
        log.info("=== 修复：使用QueryParser处理TextField的KG字段 ===");

        // 使用QueryParser构建适合TextField的查询
        // 🎯 关键修复：转义特殊字符，防止-被解析为否定操作符
        QueryParser parser = new QueryParser("KG", new StandardAnalyzer());
        String escapedKgCode = QueryParser.escape(kgCode);
        Query query = parser.parse(escapedKgCode);
        log.info("构建查询: 使用QueryParser在KG字段中匹配 原始:'{}' 转义后:'{}', 查询对象: {}", kgCode, escapedKgCode, query.toString());

        // 先获取总记录数来验证
        TopDocs testDocs = searcher.search(query, 1);
        log.info("验证查询 - KG='{}' 查询结果: {}条记录", kgCode, testDocs.totalHits.value);

        // 先获取总记录数
        TopDocs totalDocs1 = searcher.search(query, Integer.MAX_VALUE);
        long total = totalDocs1.totalHits.value;
        log.info("总记录数查询完成 - 找到{}条记录", total);
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果
        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(query, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 根据大洲进行分页检索
     * @param continent 大洲
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByContinent(Continent continent, int pageNo, int pageSize) throws Exception {
        if (continent == null) {
            throw new IllegalArgumentException("大洲不能为空");
        }
        
        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;
        
        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // 获取该大洲的所有国家
        List<String> countries = CountryContinentMapping.getCountriesByContinent(continent);
        if (countries.isEmpty()) {
            return new PageResult(new ArrayList<>(), 0);
        }
        
        // 构建多国家查询（OR查询）
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
        for (String country : countries) {
            booleanQuery.add(new TermQuery(new Term("LOCATION", country)), BooleanClause.Occur.SHOULD);
        }
        Query query = booleanQuery.build();
        
        // 先获取总记录数
        TopDocs totalDocs = searcher.search(query, Integer.MAX_VALUE);
        long total = totalDocs.totalHits.value;
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果
        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(query, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 根据国家进行分页检索
     * @param country 国家名称
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByCountry(String country, int pageNo, int pageSize) throws Exception {
        if (StringUtil.isEmpty(country)) {
            throw new IllegalArgumentException("国家名称不能为空");
        }

        log.info("IdxService查询国家 - country: {}, pageNo: {}, pageSize: {}", country, pageNo, pageSize);

        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;

        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 🎯 调试：先检查索引中实际存储的数据总量和国家/地区数据
        log.info("=== 调试：检查索引整体情况 ===");
        Query allDocsQuery = new MatchAllDocsQuery();
        TopDocs allDocs = searcher.search(allDocsQuery, Integer.MAX_VALUE);
        log.info("索引中的总记录数: {}", allDocs.totalHits.value);

        // 🎯 新增：检查workspace中的JSON文件数量（包括子目录）
        try {
            Path crawlerPath = Paths.get(config.getCrawler());
            if (Files.exists(crawlerPath) && Files.isDirectory(crawlerPath)) {
                // 检查根目录的JSON文件
                long rootJsonCount = Files.list(crawlerPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .count();

                // 递归检查所有子目录的JSON文件
                long totalJsonCount = Files.walk(crawlerPath)
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.toString().endsWith(".json"))
                    .count();

                log.info("=== 调试：检查workspace中的JSON文件 ===");
                log.info("Workspace目录: {}", config.getCrawler());
                log.info("根目录JSON文件数: {}", rootJsonCount);
                log.info("包括子目录的JSON文件总数: {}", totalJsonCount);

                // 如果JSON文件数量远大于索引记录数，说明索引没有包含所有数据
                if (totalJsonCount > allDocs.totalHits.value + 1000) { // 加1000的容错
                    log.warn("⚠️ 发现数据不一致！JSON文件有{}个，但索引只有{}条记录", totalJsonCount, allDocs.totalHits.value);
                    log.warn("建议：可能需要从workspace重建索引以包含所有数据");
                } else if (totalJsonCount == 0) {
                    log.warn("⚠️ 警告：workspace目��中没有JSON文件，索引可能过时");
                } else {
                    log.info("✅ JSON文件数量({})与索引记录数({})基本匹配", totalJsonCount, allDocs.totalHits.value);
                }
            }
        } catch (Exception e) {
            log.warn("无法检查workspace中的JSON文件数量: {}", e.getMessage());
        }

        // 检查前100条记录中的LOCATION字段数据
        log.info("=== 调试：检查索引中的LOCATION字段数据 ===");
        TopDocs sampleDocs = searcher.search(allDocsQuery, 100);
        Set<String> uniqueLocations = new HashSet<>();
        int locationFieldCount = 0;

        for (ScoreDoc scoreDoc : sampleDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String[] locations = doc.getValues("LOCATION");
            if (locations.length > 0) {
                locationFieldCount++;
                for (String location : locations) {
                    if (!location.isEmpty()) {
                        uniqueLocations.add(location);
                    }
                }
            }

            // 同时记录其他字段的信息来验证数据完整性
            String id = doc.get("ID");
            String name = doc.get("NAME");
            // 显示前10条记录的详细信息
            if (uniqueLocations.size() <= 5 && scoreDoc.doc < 5) { // 只在国家很少时显示详细信息
                // 检查所有可能的字段名
                String location = doc.get("LOCATION");
                String country1 = doc.get("COUNTRY");
                String countryField = doc.get("COUNTRY_FIELD");
                log.info("详细记录{} - ID: {}, 姓名: {}, LOCATION字段: '{}', COUNTRY字段: '{}'",
                    scoreDoc.doc, id, name, location, country1);

                // 显示文档的所有字段名
                List<IndexableField> fields = doc.getFields();
                Set<String> fieldNames = new HashSet<>();
                for (IndexableField field : fields) {
                    fieldNames.add(field.name());
                }
                log.info("  所有字段名: {}", fieldNames);
            }
        }

        log.info("前{}条记录中有LOCATION字段的记录数: {}", sampleDocs.scoreDocs.length, locationFieldCount);
        log.info("索引中找到的LOCATION数据样本（共{}种）: {}", uniqueLocations.size(), uniqueLocations);

        // 🎯 修复：使用QueryParser处理TextField的LOCATION字段
        log.info("=== 修复：使用QueryParser处理TextField的LOCATION字段 ===");
        QueryParser parser = new QueryParser("LOCATION", new StandardAnalyzer());
        String escapedCountry = QueryParser.escape(country);
        Query query = parser.parse(escapedCountry);
        log.info("构建查询: 使用QueryParser在LOCATION字段中匹配 原始:'{}' 转义后:'{}', 查询对象: {}", country, escapedCountry, query.toString());

        // 如果QueryParser精确匹配没找到，尝试模糊匹配
        TopDocs testDocs = searcher.search(query, 1);
        if (testDocs.totalHits.value == 0) {
            log.warn("QueryParser��确匹配没找到结果，尝试模糊匹配");
            // 对于模糊匹配，也使用适合TextField的方式
            WildcardQuery wildcardQuery = new WildcardQuery(new Term("LOCATION", "*" + country + "*"));
            query = wildcardQuery;
            log.info("切换到模糊匹配查询 - 查询对象: {}", query.toString());
        }
        
        // 先获取总记录数
        TopDocs totalDocs = searcher.search(query, Integer.MAX_VALUE);
        long total = totalDocs.totalHits.value;
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果
        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(query, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 根据大洲和国家进行分页检索
     * @param continent 大洲
     * @param country 国家名称
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByContinentAndCountry(Continent continent, String country, int pageNo, int pageSize) throws Exception {
        if (continent == null) {
            throw new IllegalArgumentException("大洲不能为空");
        }
        if (StringUtil.isEmpty(country)) {
            throw new IllegalArgumentException("国家名称不能为空");
        }
        
        // 验证国家是否属于指定大洲
        if (!CountryContinentMapping.isCountryInContinent(country, continent)) {
            return new PageResult(new ArrayList<>(), 0);
        }
        
        // 直接调用国家查询方法
        return queryByCountry(country, pageNo, pageSize);
    }

    /**
     * 组合条件检索 - 支持多个条件同时查询
     * @param criteria 检索条件对象
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页检索结果
     */
    public PageResult queryByCombinedCriteria(SearchCriteria criteria, int pageNo, int pageSize) throws Exception {
        if (criteria == null || !criteria.hasAnyCriteria()) {
            throw new IllegalArgumentException("检索条件不能为空");
        }
        
        // 参数验证
        if (pageNo < 1) pageNo = 1;
        if (pageSize < 1) pageSize = 10;
        
        // 打开准实时索引Reader
        DirectoryReader reader = DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // 构建组合查询
        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
        
        // 关键词查询
        if (criteria.hasKeyword()) {
            try {
                Analyzer analyzer = DEFAULT_ANALYZER.getConstructor().newInstance();
                QueryParser parser = new QueryParser("NAME", analyzer);
                Query keywordQuery = parser.parse(criteria.getKeyword());
                booleanQueryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
            } catch (Exception e) {
                log.warn("关键词查询解析失败: {}", criteria.getKeyword());
            }
        }
        
        // 年龄组别查询
        if (criteria.hasAgeGroup()) {
            Query ageQuery = IntPoint.newRangeQuery("AGE_NUM", 
                criteria.getAgeGroup().getMinAge(), 
                criteria.getAgeGroup().getMaxAge());
            booleanQueryBuilder.add(ageQuery, BooleanClause.Occur.MUST);
        }
        
        // 年龄范围查询
        if (criteria.hasAgeRange()) {
            int minAge = criteria.getMinAge() != null ? criteria.getMinAge() : 0;
            int maxAge = criteria.getMaxAge() != null ? criteria.getMaxAge() : Integer.MAX_VALUE;
            Query ageRangeQuery = IntPoint.newRangeQuery("AGE_NUM", minAge, maxAge);
            booleanQueryBuilder.add(ageRangeQuery, BooleanClause.Occur.MUST);
        }
        
        // 体重级别查询
        if (criteria.hasWeightClass()) {
            Query weightQuery = new WildcardQuery(new Term("KG", "*" + criteria.getWeightClass().getCode() + "*"));
            booleanQueryBuilder.add(weightQuery, BooleanClause.Occur.MUST);
        }
        
        // 体重范围查询
        if (criteria.hasWeightRange()) {
            // 体重范围查询需要解析KG字段中的数值
            // 由于KG字段存储的是体重级别代码，我们需要特殊处理
            // 这里我们使用通配符查询来匹配体重范围
            double minWeight = criteria.getMinWeight() != null ? criteria.getMinWeight() : 0.0;
            double maxWeight = criteria.getMaxWeight() != null ? criteria.getMaxWeight() : Double.MAX_VALUE;
            
            // 构建体重范围查询
            BooleanQuery.Builder weightRangeQueryBuilder = new BooleanQuery.Builder();
            
            // 根据体重范围匹配对应的体重级别
            if (minWeight <= 60 && maxWeight >= 60) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-60*")), BooleanClause.Occur.SHOULD);
            }
            if (minWeight <= 66 && maxWeight >= 66) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-66*")), BooleanClause.Occur.SHOULD);
            }
            if (minWeight <= 73 && maxWeight >= 73) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-73*")), BooleanClause.Occur.SHOULD);
            }
            if (minWeight <= 81 && maxWeight >= 81) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-81*")), BooleanClause.Occur.SHOULD);
            }
            if (minWeight <= 90 && maxWeight >= 90) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-90*")), BooleanClause.Occur.SHOULD);
            }
            if (minWeight <= 100 && maxWeight >= 100) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*-100*")), BooleanClause.Occur.SHOULD);
            }
            if (maxWeight >= 100) {
                weightRangeQueryBuilder.add(new WildcardQuery(new Term("KG", "*+100*")), BooleanClause.Occur.SHOULD);
            }
            
            BooleanQuery weightRangeQuery = weightRangeQueryBuilder.build();
            if (weightRangeQuery.clauses().size() > 0) {
                booleanQueryBuilder.add(weightRangeQuery, BooleanClause.Occur.MUST);
            }
        }
        
        // 大洲查询
        if (criteria.hasContinent()) {
            // 获取该大洲下的所有国家（包括明确列出的国家和others）
            List<String> countries = CountryContinentMapping.getCountriesByContinentWithOthers(criteria.getContinent());
            if (!countries.isEmpty()) {
                BooleanQuery.Builder countryQueryBuilder = new BooleanQuery.Builder();
                for (String country : countries) {
                    countryQueryBuilder.add(new TermQuery(new Term("LOCATION", country)), BooleanClause.Occur.SHOULD);
                }
                booleanQueryBuilder.add(countryQueryBuilder.build(), BooleanClause.Occur.MUST);
            }
        }
        
        // 国家查询
        if (criteria.hasCountry()) {
            Query countryQuery = new TermQuery(new Term("LOCATION", criteria.getCountry()));
            booleanQueryBuilder.add(countryQuery, BooleanClause.Occur.MUST);
        }
        
        Query combinedQuery = booleanQueryBuilder.build();
        
        // 先获取总记录数
        TopDocs totalDocs = searcher.search(combinedQuery, Integer.MAX_VALUE);
        long total = totalDocs.totalHits.value;
        
        // 计算分页参数
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        
        // 如果起始索引超出范围，返回空结果

        if (fromIndex >= total) {
            return new PageResult(new ArrayList<>(), total);
        }
        
        // 在Lucene层面进行分页查询
        TopDocs docs = searcher.search(combinedQuery, toIndex);
        ScoreDoc[] hits = docs.scoreDocs;
        
        List<Document> results = new ArrayList<>();
        // 只取当前页的数据
        for (int i = fromIndex; i < Math.min(hits.length, toIndex); i++) {
            results.add(searcher.doc(hits[i].doc));
        }
        
        return new PageResult(results, total);
    }

    /**
     * 模糊匹配检索 - 支持模糊查询和相似度匹配
     * @param fuzzyKeyword 模糊关键词
     * @param similarity 相似度阈值 (0.0-1.0)
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResponse<Player> fuzzySearch(String fuzzyKeyword, Double similarity, int page, int size) {
        log.info("🎯 IdxService模糊搜索开始 - 关键词: '{}', 相似度阈值: {}, 页码: {}, 页大小: {}", fuzzyKeyword, similarity, page, size);

        // 参数验证和日志记录
        if (fuzzyKeyword == null || fuzzyKeyword.trim().isEmpty()) {
            log.warn("⚠️ 模糊搜索关键词为空，返回空结果");
            return PageResponse.of(new ArrayList<>(), page, size, 0);
        }

        if (page < 1) {
            log.warn("⚠️ 页码参数异常: {}，调整为1", page);
            page = 1;
        }

        if (size < 1) {
            log.warn("⚠️ 页大小参数异常: {}，调整为10", size);
            size = 10;
        }

        try {
            IndexReader reader = DirectoryReader.open(writer);
            IndexSearcher searcher = new IndexSearcher(reader);

            log.info("📚 索引读取器打开成功，索引文档总数: {}", reader.numDocs());

            // 构建模糊查询
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            String searchTerm = fuzzyKeyword.toLowerCase().trim();

            log.info("🔍 构建多策略模糊查询 - 搜索词: '{}'", searchTerm);

            // 1. 模糊查询 (FuzzyQuery) - 支持拼写错误和相似词
            FuzzyQuery nameFuzzyQuery = new FuzzyQuery(new Term("NAME", searchTerm), 2);
            FuzzyQuery locationFuzzyQuery = new FuzzyQuery(new Term("LOCATION", searchTerm), 2);
            queryBuilder.add(nameFuzzyQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(locationFuzzyQuery, BooleanClause.Occur.SHOULD);
            log.info("✅ FuzzyQuery构建完成 - NAME: '{}', LOCATION: '{}'", searchTerm, searchTerm);

            // 2. 通配符查询 (WildcardQuery) - 支持*和?通配符
            WildcardQuery nameWildcardQuery = new WildcardQuery(new Term("NAME", "*" + searchTerm + "*"));
            WildcardQuery locationWildcardQuery = new WildcardQuery(new Term("LOCATION", "*" + searchTerm + "*"));
            queryBuilder.add(nameWildcardQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(locationWildcardQuery, BooleanClause.Occur.SHOULD);
            log.info("✅ WildcardQuery构建完成 - NAME: '*{}*', LOCATION: '*{}*'", searchTerm, searchTerm);

            // 3. 前缀查询 (PrefixQuery) - 支持前缀匹配
            PrefixQuery namePrefixQuery = new PrefixQuery(new Term("NAME", searchTerm));
            PrefixQuery locationPrefixQuery = new PrefixQuery(new Term("LOCATION", searchTerm));
            queryBuilder.add(namePrefixQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(locationPrefixQuery, BooleanClause.Occur.SHOULD);
            log.info("✅ PrefixQuery构建完成 - NAME: '{}', LOCATION: '{}'", searchTerm, searchTerm);

            // 4. 如果提供了相似度阈值，调整查询权重
            if (similarity != null && similarity > 0.0) {
                float boost = similarity.floatValue();
                BoostQuery boostedNameQuery = new BoostQuery(nameFuzzyQuery, boost);
                BoostQuery boostedLocationQuery = new BoostQuery(locationFuzzyQuery, boost);
                queryBuilder.add(boostedNameQuery, BooleanClause.Occur.SHOULD);
                queryBuilder.add(boostedLocationQuery, BooleanClause.Occur.SHOULD);
                log.info("🔥 相似度权重应用 - 权重值: {}", boost);
            }

            BooleanQuery query = queryBuilder.build();
            log.info("🎯 最终查询语句: {}", query.toString());

            // 🔧 修复分页逻辑 - 计算正确的起始位置和总数
            int start = (page - 1) * size;  // 修复：第1页从0开始
            int totalHitsToRetrieve = start + size;  // 修复：获取足够的结果用于分页

            log.info("📄 分页计算 - 起始位置: {}, 需要获取结果数: {}", start, totalHitsToRetrieve);

            // 执行搜索
            TopDocs topDocs = searcher.search(query, totalHitsToRetrieve);
            log.info("🎉 搜索完成 - 总命中数: {}, 实际获取文档数: {}", topDocs.totalHits.value, topDocs.scoreDocs.length);

            // 分页处理 - 从起始位置开始提取数据
            int actualStart = Math.max(0, start);
            int actualEnd = Math.min(actualStart + size, topDocs.scoreDocs.length);

            log.info("✂️ 结果切片 - 实际起始: {}, 实际结束: {}", actualStart, actualEnd);

            List<Player> players = new ArrayList<>();
            for (int i = actualStart; i < actualEnd; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.doc(scoreDoc.doc);
                Player player = documentToPlayer(doc);

                if (player != null) {
                    players.add(player);
                    log.debug("👤 成功解析运动员数据 - ID: {}, 姓名: {}", player.getId(), player.getName());
                } else {
                    log.warn("⚠️ 文档转换为Player对象失败，文档ID: {}", scoreDoc.doc);
                }
            }

            reader.close();

            log.info("🏆 模糊搜索成功完成 - 返回{}条记录，总匹配数: {}", players.size(), topDocs.totalHits.value);
            return PageResponse.of(players, page, size, topDocs.totalHits.value);

        } catch (Exception e) {
            log.error("💥 模糊搜索执行失败 - 关键词: '{}', 错误: {}", fuzzyKeyword, e.getMessage(), e);
            throw new RuntimeException("模糊搜索执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 高级搜索 - 多字段组合检索
     * 支持复杂的布尔逻辑组合，包括AND、OR、NOT操作
     * @param criteria 检索条件
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResponse<Player> advancedSearch(SearchCriteria criteria, int page, int size) {
        try {
            IndexReader reader = DirectoryReader.open(writer);
            IndexSearcher searcher = new IndexSearcher(reader);
            
            BooleanQuery.Builder mainQueryBuilder = new BooleanQuery.Builder();
            
            // 1. 关键词检索 (精确匹配 + 模糊匹配)
            if (criteria.hasKeyword()) {
                BooleanQuery.Builder keywordQueryBuilder = new BooleanQuery.Builder();
                
                // 精确匹配
                TermQuery nameQuery = new TermQuery(new Term("NAME", criteria.getKeyword().toLowerCase()));
                TermQuery countryQuery = new TermQuery(new Term("LOCATION", criteria.getKeyword().toLowerCase()));

                // 短语查询 - 提高精确度
                PhraseQuery namePhraseQuery = new PhraseQuery.Builder()
                    .add(new Term("NAME", criteria.getKeyword().toLowerCase()))
                    .build();
                
                keywordQueryBuilder.add(nameQuery, BooleanClause.Occur.SHOULD);
                keywordQueryBuilder.add(countryQuery, BooleanClause.Occur.SHOULD);
                keywordQueryBuilder.add(namePhraseQuery, BooleanClause.Occur.SHOULD);
                
                mainQueryBuilder.add(keywordQueryBuilder.build(), BooleanClause.Occur.MUST);
            }
            
            // 2. 模糊关键词检索
            if (criteria.hasFuzzyKeyword()) {
                BooleanQuery.Builder fuzzyQueryBuilder = new BooleanQuery.Builder();
                
                // 模糊查询
                FuzzyQuery nameFuzzyQuery = new FuzzyQuery(new Term("NAME", criteria.getFuzzyKeyword().toLowerCase()), 2);
                FuzzyQuery locationFuzzyQuery = new FuzzyQuery(new Term("LOCATION", criteria.getFuzzyKeyword().toLowerCase()), 2);
                
                // 通配符查询
                WildcardQuery nameWildcardQuery = new WildcardQuery(new Term("NAME", "*" + criteria.getFuzzyKeyword().toLowerCase() + "*"));
                WildcardQuery locationWildcardQuery = new WildcardQuery(new Term("LOCATION", "*" + criteria.getFuzzyKeyword().toLowerCase() + "*"));
                
                fuzzyQueryBuilder.add(nameFuzzyQuery, BooleanClause.Occur.SHOULD);
                fuzzyQueryBuilder.add(locationFuzzyQuery, BooleanClause.Occur.SHOULD);
                fuzzyQueryBuilder.add(nameWildcardQuery, BooleanClause.Occur.SHOULD);
                fuzzyQueryBuilder.add(locationWildcardQuery, BooleanClause.Occur.SHOULD);
                
                mainQueryBuilder.add(fuzzyQueryBuilder.build(), BooleanClause.Occur.MUST);
            }
            
            // 3. 年龄组别检索
            if (criteria.hasAgeGroup()) {
                TermQuery ageGroupQuery = new TermQuery(new Term("AGE", criteria.getAgeGroup().name()));
                mainQueryBuilder.add(ageGroupQuery, BooleanClause.Occur.MUST);
            }
            
            // 4. 年龄范围检索
            if (criteria.hasAgeRange()) {
                Integer minAge = criteria.getMinAge();
                Integer maxAge = criteria.getMaxAge();
                
                if (minAge == null) minAge = 0;
                if (maxAge == null) maxAge = 150;
                
                // 使用IntPoint进行范围查询
                Query ageRangeQuery = IntPoint.newRangeQuery("AGE_NUM", minAge, maxAge);
                mainQueryBuilder.add(ageRangeQuery, BooleanClause.Occur.MUST);
            }
            
            // 5. 体重级别检索
            if (criteria.hasWeightClass()) {
                Query weightClassQuery = new WildcardQuery(new Term("KG", "*" + criteria.getWeightClass().getCode() + "*"));
                mainQueryBuilder.add(weightClassQuery, BooleanClause.Occur.MUST);
            }
            
            // 6. 体重范围检索
            if (criteria.hasWeightRange()) {
                Double minWeight = criteria.getMinWeight();
                Double maxWeight = criteria.getMaxWeight();
                
                if (minWeight == null) minWeight = 0.0;
                if (maxWeight == null) maxWeight = 500.0;
                
                // 使用DoublePoint进行范围查询
                Query weightRangeQuery = DoublePoint.newRangeQuery("WEIGHT", minWeight, maxWeight);
                mainQueryBuilder.add(weightRangeQuery, BooleanClause.Occur.MUST);
            }
            
            // 7. 大洲检索
            if (criteria.hasContinent()) {
                BooleanQuery.Builder continentQueryBuilder = new BooleanQuery.Builder();
                
                // 精确匹配大洲
                TermQuery continentQuery = new TermQuery(new Term("CONTINENT", criteria.getContinent().name()));
                continentQueryBuilder.add(continentQuery, BooleanClause.Occur.SHOULD);

                // 获取该大洲的所有国家（包括others）
                List<String> allCountries = CountryContinentMapping.getCountriesByContinentWithOthers(criteria.getContinent());
                for (String country : allCountries) {
                    TermQuery countryQuery = new TermQuery(new Term("LOCATION", country));
                    continentQueryBuilder.add(countryQuery, BooleanClause.Occur.SHOULD);
                }
                
                mainQueryBuilder.add(continentQueryBuilder.build(), BooleanClause.Occur.MUST);
            }
            
            // 8. 国家检索
            if (criteria.hasCountry()) {
                TermQuery countryQuery = new TermQuery(new Term("LOCATION", criteria.getCountry()));
                mainQueryBuilder.add(countryQuery, BooleanClause.Occur.MUST);
            }
            
            BooleanQuery mainQuery = mainQueryBuilder.build();
            
            // 执行搜索
            TopDocs topDocs = searcher.search(mainQuery, page * size);
            
            // 分页处理
            int start = page * size;
            int end = Math.min(start + size, topDocs.scoreDocs.length);
            
            List<Player> players = new ArrayList<>();
            for (int i = start; i < end; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.doc(scoreDoc.doc);
                Player player = documentToPlayer(doc);
                players.add(player);
            }
            
            reader.close();
            
            return PageResponse.of(players, page, size, topDocs.totalHits.value);
            
        } catch (Exception e) {
            log.error("高级搜索失败", e);
            throw new RuntimeException("高级搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 智能搜索 - 结合精确匹配和模糊匹配的智能检索
     * 优先返回精确匹配结果，然后返回模糊匹配结果
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResponse<Player> smartSearch(String keyword, int page, int size) {
        try {
            IndexReader reader = DirectoryReader.open(writer);
            IndexSearcher searcher = new IndexSearcher(reader);
            
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            
            // 1. 精确匹配 (高权重)
            TermQuery exactNameQuery = new TermQuery(new Term("NAME", keyword.toLowerCase()));
            TermQuery exactCountryQuery = new TermQuery(new Term("LOCATION", keyword.toLowerCase()));
            
            BoostQuery boostedExactNameQuery = new BoostQuery(exactNameQuery, 3.0f);
            BoostQuery boostedExactCountryQuery = new BoostQuery(exactCountryQuery, 2.0f);
            
            queryBuilder.add(boostedExactNameQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(boostedExactCountryQuery, BooleanClause.Occur.SHOULD);
            
            // 2. 短语匹配 (中权重)
            PhraseQuery namePhraseQuery = new PhraseQuery.Builder()
                .add(new Term("NAME", keyword.toLowerCase()))
                .build();
            
            BoostQuery boostedPhraseQuery = new BoostQuery(namePhraseQuery, 2.5f);
            queryBuilder.add(boostedPhraseQuery, BooleanClause.Occur.SHOULD);
            
            // 3. 前缀匹配 (中权重)
            PrefixQuery namePrefixQuery = new PrefixQuery(new Term("NAME", keyword.toLowerCase()));
            PrefixQuery countryPrefixQuery = new PrefixQuery(new Term("LOCATION", keyword.toLowerCase()));
            
            BoostQuery boostedNamePrefixQuery = new BoostQuery(namePrefixQuery, 2.0f);
            BoostQuery boostedCountryPrefixQuery = new BoostQuery(countryPrefixQuery, 1.5f);
            
            queryBuilder.add(boostedNamePrefixQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(boostedCountryPrefixQuery, BooleanClause.Occur.SHOULD);
            
            // 4. 模糊匹配 (低权重)
            FuzzyQuery nameFuzzyQuery = new FuzzyQuery(new Term("NAME", keyword.toLowerCase()), 2);
            FuzzyQuery locationFuzzyQuery = new FuzzyQuery(new Term("LOCATION", keyword.toLowerCase()), 2);

            BoostQuery boostedNameFuzzyQuery = new BoostQuery(nameFuzzyQuery, 1.0f);
            BoostQuery boostedLocationFuzzyQuery = new BoostQuery(locationFuzzyQuery, 0.8f);

            queryBuilder.add(boostedNameFuzzyQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(boostedLocationFuzzyQuery, BooleanClause.Occur.SHOULD);
            
            // 5. 通配符匹配 (最低权重)
            WildcardQuery nameWildcardQuery = new WildcardQuery(new Term("NAME", "*" + keyword.toLowerCase() + "*"));
            WildcardQuery locationWildcardQuery = new WildcardQuery(new Term("LOCATION", "*" + keyword.toLowerCase() + "*"));
            
            BoostQuery boostedNameWildcardQuery = new BoostQuery(nameWildcardQuery, 0.5f);
            BoostQuery boostedCountryWildcardQuery = new BoostQuery(locationWildcardQuery, 0.3f);
            
            queryBuilder.add(boostedNameWildcardQuery, BooleanClause.Occur.SHOULD);
            queryBuilder.add(boostedCountryWildcardQuery, BooleanClause.Occur.SHOULD);
            
            BooleanQuery query = queryBuilder.build();
            
            // 执行搜索
            TopDocs topDocs = searcher.search(query, page * size);
            
            // 分页处理
            int start = page * size;
            int end = Math.min(start + size, topDocs.scoreDocs.length);
            
            List<Player> players = new ArrayList<>();
            for (int i = start; i < end; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.doc(scoreDoc.doc);
                Player player = documentToPlayer(doc);
                players.add(player);
            }
            
            reader.close();
            
            return PageResponse.of(players, page, size, topDocs.totalHits.value);
            
        } catch (Exception e) {
            log.error("智能搜索失败", e);
            throw new RuntimeException("智能搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 将Document转换为Player对象
     * @param doc Lucene文档
     * @return Player对象
     */
    private Player documentToPlayer(Document doc) {
        log.debug("🔍 开始转换Document到Player对象 - 文档ID: {}", doc.get("ID"));

        Player player = new Player();
        player.setId(doc.get("ID"));
        player.setName(doc.get("NAME"));

        // 🔧 修复：使用正确的字段名并添加详细日志
        String location = doc.get("LOCATION");
        String age = doc.get("AGE");
        String kg = doc.get("KG");
        String image = doc.get("IMAGE");
        String locationIcon = doc.get("LOCATION_ICON");

        player.setLocation(location);
        player.setAge(age);
        player.setKg(kg);
        player.setImage(image);
        player.setLocationIcon(locationIcon);

        log.debug("📋 字段提取结果 - ID: {}, NAME: {}, LOCATION: {}, AGE: {}, KG: {}, IMAGE存在: {}, LOCATION_ICON存在: {}",
                 doc.get("ID"), doc.get("NAME"), location, age, kg, image != null, locationIcon != null);

        // 🔧 新增：处理PHOTOS字段
        String photosJson = doc.get("PHOTOS");
        if (photosJson != null && !photosJson.trim().isEmpty() && !photosJson.equals("[]")) {
            try {
                PhotoEntity photoEntity = JsonUtils.fromJson(photosJson, PhotoEntity.class);
                player.setPhotoEntity(photoEntity);
                log.debug("📸 成功解析PHOTOS字段 - ID: {}", doc.get("ID"));
            } catch (Exception e) {
                log.warn("⚠️ 解析PHOTOS字段失败 - ID: {}, 错误: {}", doc.get("ID"), e.getMessage());
                player.setPhotoEntity(null);
            }
        } else {
            log.debug("📷 PHOTOS字段为空或未设置 - ID: {}", doc.get("ID"));
            player.setPhotoEntity(null);
        }

        log.debug("✅ Document转换完成 - Player对象: {}", player);
        return player;
    }

    /**
     * 🎯 新增：从workspace重建索引以包含所有数据
     * @return 重建的记录数
     */
    public int rebuildIndexFromWorkspace() {
        if (writer == null) {
            log.error("IndexWriter未初始化，无法重建索引");
            return -1;
        }

        log.info("=== 开始从workspace重建索引 ===");
        Path crawlerPath = Paths.get(config.getCrawler());

        if (!Files.exists(crawlerPath) || !Files.isDirectory(crawlerPath)) {
            log.error("Workspace目录不存在或不是目录: {}", config.getCrawler());
            return -1;
        }

        try {
            // 先清空现有索引
            log.info("清空现有索引...");
            writer.deleteAll();
            writer.commit();
            log.info("索引已清空");

            ObjectMapper objectMapper = new ObjectMapper();
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            // 遍历所有JSON文件并重建索引
            try (Stream<Path> jsonFiles = Files.walk(crawlerPath)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().endsWith(".json"))) {

                long totalFiles = jsonFiles.count();
                log.info("找到{}个JSON文件，开始重建索引...", totalFiles);

                // 重新打开流来处理文件
                try (Stream<Path> jsonFiles2 = Files.walk(crawlerPath)
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.toString().endsWith(".json"))) {

                    jsonFiles2.forEach(jsonPath -> {
                        try {
                            // 读取JSON文件
                            JsonNode jsonNode = objectMapper.readTree(jsonPath.toFile());

                            // 🎯 修复：正确提取嵌套JSON结构中的字段
                            String id = "unknown", name = "未知", age = "未知", image = "未提供", location = "未知", locationIcon = "未提供", kg = "未知";

                            // 尝试从BLOG_INFO节点提取数据
                            if (jsonNode.has("BLOG_INFO")) {
                                JsonNode blogInfo = jsonNode.get("BLOG_INFO");
                                id = blogInfo.has("id") ? blogInfo.get("id").asText() : "unknown";
                                name = blogInfo.has("name") ? blogInfo.get("name").asText() : "未知";
                                age = blogInfo.has("age") ? blogInfo.get("age").asText() : "未知";
                                image = blogInfo.has("image") ? blogInfo.get("image").asText() : "未提供";
                                location = blogInfo.has("location") ? blogInfo.get("location").asText() : "未知";
                                locationIcon = blogInfo.has("locationIcon") ? blogInfo.get("locationIcon").asText() : "未提供";
                                kg = blogInfo.has("kg") ? blogInfo.get("kg").asText() : "未知";

                                log.debug("提取数据成功 - ID: {}, 姓名: {}, 国家: {}", id, name, location);
                            } else {
                                // 兼容性：尝试从顶级字段提取（用于可能的其他格式JSON文件）
                                id = jsonNode.has("id") ? jsonNode.get("id").asText() : "unknown";
                                name = jsonNode.has("name") ? jsonNode.get("name").asText() : "未知";
                                age = jsonNode.has("age") ? jsonNode.get("age").asText() : "未知";
                                image = jsonNode.has("image") ? jsonNode.get("image").asText() : "未提供";
                                location = jsonNode.has("location") ? jsonNode.get("location").asText() : "未知";
                                locationIcon = jsonNode.has("locationIcon") ? jsonNode.get("locationIcon").asText() : "未提供";
                                kg = jsonNode.has("kg") ? jsonNode.get("kg").asText() : "未知";

                                log.warn("JSON文件没有BLOG_INFO节点，使用顶级字段 - ID: {}", id);
                            }

                            // 创建Lucene文档
                            Document doc = new Document();
                            doc.add(new StringField("ID", id, Field.Store.YES));
                            doc.add(new TextField("NAME", name, Field.Store.YES));
                            doc.add(new TextField("AGE", age, Field.Store.YES));
                            doc.add(new TextField("IMAGE", image, Field.Store.YES));
                            doc.add(new TextField("LOCATION", location, Field.Store.YES));
                            doc.add(new TextField("LOCATION_ICON", locationIcon, Field.Store.YES));
                            doc.add(new TextField("KG", kg, Field.Store.YES));

                            // 添加到索引
                            writer.updateDocument(new Term("ID", id), doc);

                            processedCount.incrementAndGet();

                            // 每1000条记录提交一次，并记录统计信息
                            if (processedCount.get() % 1000 == 0) {
                                writer.commit();
                                log.info("已处理{}条记录...", processedCount.get());
                            }

                            // 每处理100条记录，抽样统计一次国家分布
                            if (processedCount.get() % 100 == 0) {
                                log.info("抽样 - 当前记录: ID={}, 姓名={}, 国家={}", id, name, location);
                            }

                        } catch (Exception e) {
                            log.error("处理JSON文件失败: {}, 错误: {}", jsonPath, e.getMessage());
                            errorCount.incrementAndGet();
                        }
                    });
                }
            }

            // 最终提交
            writer.commit();
            log.info("=== 索引重建完成 ===");
            log.info("总共处理: {} 条记录", processedCount.get());
            log.info("处理失败: {} 条记录", errorCount.get());
            log.info("成功重建: {} 条记录", processedCount.get() - errorCount.get());

            return processedCount.get() - errorCount.get();

        } catch (Exception e) {
            log.error("重建索引过程中发生错误: {}", e.getMessage(), e);
            try {
                writer.rollback();
            } catch (IOException ioException) {
                log.error("回滚索引失败: {}", ioException.getMessage());
            }
            return -1;
        }
    }

    @Override
    public void destroy(){
        if(this.writer==null){
            return;
        }
        try {
            log.info("索引关闭");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            log.info("尝试关闭索引失败");
        }
    }
}
