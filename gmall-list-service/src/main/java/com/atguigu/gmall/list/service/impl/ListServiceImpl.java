package com.atguigu.gmall.list.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.bean.SkuLsParams;
import com.atguigu.gmall.bean.SkuLsResult;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.service.ListService;
import io.searchbox.client.JestClient;
import io.searchbox.core.*;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ListServiceImpl implements ListService {

    @Autowired
    private JestClient jestClient;

    public static final String ES_INDEX="gmall";

    public static final String ES_TYPE="SkuInfo";

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 保存数据
     *
     * @param skuLsInfo
     */
    @Override
    public void saveSkuInfo(SkuLsInfo skuLsInfo) {

        //创建Index.Builder()
        Index index = new Index.Builder(skuLsInfo).index(ES_INDEX).type(ES_TYPE).id(skuLsInfo.getId()).build();
        try {
            //执行保存
            DocumentResult documentResult = jestClient.execute(index);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 查询es中的数据
     *
     * @param skuLsParams
     * @return
     */
    @Override
    public SkuLsResult search(SkuLsParams skuLsParams) {
        //先获取dsl语句
        String query = makeQueryStringForSearch(skuLsParams);
        //准备执行
        Search search = new Search.Builder(query).addIndex(ES_INDEX).addType(ES_TYPE).build();

        SearchResult searchResult = null;
        try {
            searchResult = jestClient.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //将es结果集转换为skuLsResult ，不能直接转需要自定义方法 makeResultForSearch
        SkuLsResult skuLsResult = makeResultForSearch(searchResult,skuLsParams);
        return skuLsResult;
    }

    /**
     * 热度计数器
     *
     * @param skuId
     */
    @Override
    public void incrHotScore(String skuId) {
        //获取jedis
        Jedis jedis = redisUtil.getJedis();

        //hotScore增长之后的数量
        Double hotScore = jedis.zincrby("hotScore", 1, "skuId" + skuId);
        //定义一个常量 当hotScore为10的倍数时 更新一次es中的数据
        int timesToEs=10;
        if (hotScore % timesToEs == 0){
            //更新es   Math.round(hotScore)取公约数
            updateHotScore(skuId,  Math.round(hotScore));

        }
    }

    private void updateHotScore(String skuId, long hotScore) {
        //更新es
        //先写dsl语句
        String updateJson="{\n" +
                "   \"doc\":{\n" +
                "     \"hotScore\":"+hotScore+"\n" +
                "   }\n" +
                "}";
        Update update = new Update.Builder(updateJson).index(ES_INDEX).type(ES_TYPE).id(skuId).build();

        //准备执行
        try {
            jestClient.execute(update);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *将es结果集转换为skuLsResult
     * 思路就是  通过把在es中存的值拿到  然后赋值给SkuLsResult中的属性  完成转换
     * @param searchResult
     * @param skuLsParams
     * @return
     */
    private SkuLsResult makeResultForSearch(SearchResult searchResult, SkuLsParams skuLsParams) {
        SkuLsResult skuLsResult = new SkuLsResult();
        //skuInfo集合
        //List<SkuLsInfo> skuLsInfoList;
        //总条数
        //long total;
        //总页数// long totalPages;
        //获取平台属性值ID集合， 为了能够在页面上渲染出平平台属性名 平台属性值名称
        //List<String> attrValueIdList;

        //声明一个集合用来封装对象
        List<SkuLsInfo> skuLsInfoArrayList = new ArrayList<>();
        //先给skuLsInfoList赋值
        List<SearchResult.Hit<SkuLsInfo, Void>> hits = searchResult.getHits(SkuLsInfo.class);
        //遍历该结果集
        for (SearchResult.Hit<SkuLsInfo, Void> hit : hits) {
            //数据在source节点上  得到每一个skuLsInfo
            SkuLsInfo skuLsInfo = hit.source;
            //将skuLsInfo中的skuName换成高亮的skuName
            if (hit.highlight != null && hit.highlight.size() > 0 ){
                List<String> list = hit.highlight.get("skuName");
                //高亮名称
                String skuNameH1 = list.get(0);
                skuLsInfo.setSkuName(skuNameH1);

            }

            //把skuLsInfo放到skuLsInfoArrayList
            skuLsInfoArrayList.add(skuLsInfo);
        }
        //给skuLsInfoList赋值
        skuLsResult.setSkuLsInfoList(skuLsInfoArrayList);
        //赋值总条数
        skuLsResult.setTotal(searchResult.getTotal());
        //赋值总页数
        long totalPages = (searchResult.getTotal() + skuLsParams.getPageSize() - 1) / skuLsParams.getPageSize();
        skuLsResult.setTotalPages(totalPages);

        //声明一个集合来存放平台属性值id
        List<String> attrValueIdList = new ArrayList<>();

        //赋值attrValueIdList平台属性集合
        MetricAggregation aggregations = searchResult.getAggregations();
        TermsAggregation groupby_attr = aggregations.getTermsAggregation("groupby_attr");
        List<TermsAggregation.Entry> buckets = groupby_attr.getBuckets();
        if (buckets != null && buckets.size() > 0){
            //循环取出平台属性值id
            for (TermsAggregation.Entry bucket : buckets) {
                String valueId = bucket.getKey();
                attrValueIdList.add(valueId);
            }
        }
        //将集合放入skuLsResult
        skuLsResult.setAttrValueIdList(attrValueIdList);

        return skuLsResult;
    }



    /**
     * 动态生成dsl语句
     * @param skuLsParams
     * @return
     */
    private String makeQueryStringForSearch(SkuLsParams skuLsParams) {
        //构建一个查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //创建 bool
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //创建过滤条件

        //创建查询条件
        if (skuLsParams.getKeyword() != null && skuLsParams.getKeyword().length() > 0){
            //must   match
            MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("skuName",skuLsParams.getKeyword());
            //将must添加到bool
            boolQueryBuilder.must(matchQueryBuilder);
            //设置高亮
            HighlightBuilder highlighter = searchSourceBuilder.highlighter();
            //设置高亮字段,以及前后缀
            highlighter.field("skuName");
            highlighter.preTags("<span style='color:red'>");
            highlighter.postTags("</span>");
            searchSourceBuilder.highlight(highlighter);
        }
        //创建过滤条件  三级分类Id
        if (skuLsParams.getCatalog3Id() != null && skuLsParams.getCatalog3Id().length() > 0){
            //filter  term
            TermQueryBuilder termQueryBuilder = new TermQueryBuilder("catalog3Id",skuLsParams.getCatalog3Id());
            boolQueryBuilder.filter(termQueryBuilder);
        }
        //创建过滤条件  平台属性值id
        //要注意这边的id是数组
        if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0){
            for (String valueId: skuLsParams.getValueId()) {
                TermQueryBuilder termQueryBuilder = new TermQueryBuilder("skuAttrValueList.valueId",valueId);
                boolQueryBuilder.filter(termQueryBuilder);
            }
        }
        //设置排排序
        searchSourceBuilder.sort("hotScore", SortOrder.DESC);
        //设置分页  分页公式(pageNo - 1) * pageSize
        int from = (skuLsParams.getPageNo()-1) * skuLsParams.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(skuLsParams.getPageSize());

        // 设置聚合
        TermsBuilder groupby_attr = AggregationBuilders.terms("groupby_attr").field("skuAttrValueList.valueId");
        searchSourceBuilder.aggregation(groupby_attr);

        //调用方法
        searchSourceBuilder.query(boolQueryBuilder);

        String query = searchSourceBuilder.toString();

        System.out.println(query);

        return query;
    }

}
