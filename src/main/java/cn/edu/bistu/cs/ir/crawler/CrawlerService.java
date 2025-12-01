package cn.edu.bistu.cs.ir.crawler;

import cn.edu.bistu.cs.ir.config.Config;
import cn.edu.bistu.cs.ir.index.IdxService;
import cn.edu.bistu.cs.ir.index.LucenePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.JsonFilePipeline;
import us.codecraft.webmagic.scheduler.PriorityScheduler;

import javax.annotation.PostConstruct;

import static us.codecraft.webmagic.Spider.Status.Stopped;

/**
 * 面向爬虫的服务类
 * @author zhaxijiancuo
 */
@Component
public class CrawlerService{

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final Config config;

    private final IdxService idxService;

    public CrawlerService(@Autowired Config config,
                          @Autowired IdxService idxService){
        this.config = config;
        this.idxService = idxService;
    }

    private Spider spider = null;


    /**
     * 启动面向国际柔道联盟的爬虫
     * @param blogger 待爬取的运动员ID
     */
    public void startCnBlogCrawler(String blogger) {
        String startPage = "https://www.ijf.org/judoka";

        if(this.spider != null){
            if(!Stopped.equals(this.spider.getStatus())){
                // 如果spider成员不为空，并且状态不是 Stopped，则不可以启动新的爬虫
                log.error("当前有正在运行的爬虫对象，不可以创建新的爬虫");
                return;
            }
        }
        Site site = Site
                .me()
                .setRetryTimes(config.getRetryTimes())
                .setSleepTime(config.getSleepTime())
                .setUserAgent(config.getAgent());
        this.spider = Spider.create(new IjfCrawler(site));
        spider.addPipeline(new LucenePipeline(idxService));
        spider.addPipeline(new JsonFilePipeline(config.getCrawler()));
        spider.setScheduler(new PriorityScheduler());
        spider.thread(1);
        spider.addUrl(startPage);
        spider.runAsync();
        // log.info("启动面向国际柔道联盟的爬虫，抓取选手ID为[{}]的柔道选手的信息", blogger);
    }

    @PostConstruct
    public void init(){
        // 🔍 关键诊断日志：启动时的数据摄入检查
        log.error("🔍 JudoIngest - 开始数据摄入诊断");
        log.error("🔍 JudoIngest - 配置检查 - startCrawler: {}", config.isStartCrawler());

        // 📂 检查本地数据目录
        java.nio.file.Path crawlerPath = java.nio.file.Paths.get(config.getCrawler());
        log.error("📂 JudoIngest - 扫描目录: {}", crawlerPath.toAbsolutePath());

        if (java.nio.file.Files.exists(crawlerPath) && java.nio.file.Files.isDirectory(crawlerPath)) {
            try {
                // 统计JSON文件数量
                long jsonFileCount = java.nio.file.Files.walk(crawlerPath)
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .count();

                log.error("📄 JudoIngest - 找到 {} 个JSON文件到索引", jsonFileCount);

                if (jsonFileCount > 0) {
                    // 🎯 关键决策：有本地数据时，应该优先从本地重建索引
                    log.error("🎯 JudoIngest - 发现本地数据，尝试重建索引而不是启动爬虫");
                    try {
                        int rebuiltRecords = idxService.rebuildIndexFromWorkspace();
                        log.error("✅ JudoIngest - 重建索引完成，处理了 {} 条记录", rebuiltRecords);
                    } catch (Exception e) {
                        log.error("❌ JudoIngest - 重建索引失败", e);
                        // 如果重建失败，仍然尝试启动爬虫
                    }
                } else {
                    log.error("⚠️ JudoIngest - 本地目录为空，将启动网络爬虫");
                }

            } catch (Exception e) {
                log.error("❌ JudoIngest - 扫描本地目录失败", e);
            }
        } else {
            log.error("❌ JudoIngest - 本地目录不存在或无法访问: {}", config.getCrawler());
        }

        // 🌐 原有爬虫逻辑
        if(config.isStartCrawler()){
            log.error("🌐 JudoIngest - 配置启动网络爬虫 - startCrawler=true");
            startCnBlogCrawler("all_male");
        } else {
            log.error("🌐 JudoIngest - 配置禁用网络爬虫 - startCrawler=false");
        }

        log.error("🔍 JudoIngest - 数据摄入诊断完成");
    }
}
