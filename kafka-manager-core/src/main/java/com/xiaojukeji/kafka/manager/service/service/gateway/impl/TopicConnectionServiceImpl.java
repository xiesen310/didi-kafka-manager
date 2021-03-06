package com.xiaojukeji.kafka.manager.service.service.gateway.impl;

import com.xiaojukeji.kafka.manager.common.entity.pojo.gateway.TopicConnectionDO;
import com.xiaojukeji.kafka.manager.common.entity.ao.topic.TopicConnection;
import com.xiaojukeji.kafka.manager.common.constant.KafkaConstant;
import com.xiaojukeji.kafka.manager.common.utils.ValidateUtils;
import com.xiaojukeji.kafka.manager.dao.gateway.TopicConnectionDao;
import com.xiaojukeji.kafka.manager.service.cache.PhysicalClusterMetadataManager;
import com.xiaojukeji.kafka.manager.service.service.gateway.TopicConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zyk
 */
@Service("topicConnectionService")
public class TopicConnectionServiceImpl implements TopicConnectionService {
    private final static Logger LOGGER = LoggerFactory.getLogger(TopicConnectionServiceImpl.class);

    @Autowired
    private TopicConnectionDao topicConnectionDao;

    @Override
    public void batchAdd(List<TopicConnectionDO> doList) {
        if (ValidateUtils.isEmptyList(doList)) {
            return;
        }

        int count = 0;
        for (TopicConnectionDO connectionDO: doList) {
            try {
                count += topicConnectionDao.replace(connectionDO);
            } catch (Exception e) {
                LOGGER.error("class=TopicConnectionServiceImpl||method=batchAdd||connectionDO={}||errMsg={}", connectionDO, e.getMessage());
            }
        }
        LOGGER.info("class=TopicConnectionServiceImpl||method=batchAdd||allSize={}||successSize={}", doList.size(), count);
    }

    @Override
    public List<TopicConnection> getByTopicName(Long clusterId,
                                                String topicName,
                                                Date startTime,
                                                Date endTime) {
        List<TopicConnectionDO> doList = null;
        try {
            doList = topicConnectionDao.getByTopicName(clusterId, topicName, startTime, endTime);
        } catch (Exception e) {
            LOGGER.error("get topic connections failed, clusterId:{} topicName:{}.", clusterId, topicName, e);
        }
        if (ValidateUtils.isEmptyList(doList)) {
            return new ArrayList<>();
        }
        return getByTopicName(clusterId, doList);
    }

    @Override
    public List<TopicConnection> getByTopicName(Long clusterId,
                                                String topicName,
                                                String appId,
                                                Date startTime,
                                                Date endTime) {
        List<TopicConnectionDO> doList = null;
        try {
            doList = topicConnectionDao.getByTopicName(clusterId, topicName, startTime, endTime);
        } catch (Exception e) {
            LOGGER.error("get topic connections failed, clusterId:{} topicName:{}.", clusterId, topicName, e);
        }
        if (ValidateUtils.isEmptyList(doList)) {
            return new ArrayList<>();
        }
        return getByTopicName(
                clusterId,
                doList.stream().filter(elem -> elem.getAppId().equals(appId)).collect(Collectors.toList())
        );
    }

    @Override
    public List<TopicConnection> getByAppId(String appId, Date startTime, Date endTime) {
        List<TopicConnectionDO> doList = null;
        try {
            doList = topicConnectionDao.getByAppId(appId, startTime, endTime);
        } catch (Exception e) {
            LOGGER.error("get topic connections failed, appId:{} .", appId, e);
        }
        if (ValidateUtils.isEmptyList(doList)) {
            return new ArrayList<>();
        }
        return getByTopicName(null, doList);
    }

    @Override
    public boolean isExistConnection(Long clusterId,
                                    String topicName,
                                    Date startTime,
                                    Date endTime) {
        List<TopicConnection> connectionList = this.getByTopicName(
                clusterId,
                topicName,
                startTime,
                endTime);
        if (!ValidateUtils.isEmptyList(connectionList)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isExistConnection(Long clusterId,
                                     String topicName,
                                     String appId,
                                     Date startTime,
                                     Date endTime) {
        List<TopicConnection> connectionList = this.getByTopicName(
                clusterId,
                topicName,
                appId,
                startTime,
                endTime);
        if (!ValidateUtils.isEmptyList(connectionList)) {
            return true;
        }
        return false;
    }

    private List<TopicConnection> getByTopicName(Long clusterId, List<TopicConnectionDO> doList) {
        if (ValidateUtils.isEmptyList(doList)) {
            return new ArrayList<>();
        }
        Set<String> brokerHostnameSet = new HashSet<>();
        if (!ValidateUtils.isNull(clusterId)) {
            brokerHostnameSet = PhysicalClusterMetadataManager.getBrokerHostnameSet(clusterId);
        }

        // ???????????????????????????????????????
        Map<String, List<TopicConnection>> dtoListMap = new HashMap<>();
        for (TopicConnectionDO connectionDO: doList) {
            //??????????????????????????????, ?????????????????????-1?????????
            String deDuplicateKey = connectionDO.uniqueKey();
            List<TopicConnection> dtoList = dtoListMap.getOrDefault(deDuplicateKey, new ArrayList<>());
            if (!dtoList.isEmpty() && (
                    KafkaConstant.CLIENT_VERSION_CODE_UNKNOWN.equals(connectionDO.getClientVersion())
                            || KafkaConstant.CLIENT_VERSION_NAME_UNKNOWN.equals(connectionDO.getClientVersion()))
                    ) {
                // ?????? && connectionDO???Version_UNKNOWN
                continue;
            }
            if (!dtoList.isEmpty() && (
                    KafkaConstant.CLIENT_VERSION_NAME_UNKNOWN.equals(dtoList.get(0).getClientVersion())
                            || KafkaConstant.CLIENT_VERSION_CODE_UNKNOWN.equals(dtoList.get(0).getClientVersion())
            )) {
                // ?????? && dtoList?????????Version_UNKNOWN
                dtoList.remove(0);
            }
            TopicConnection dto = convert2TopicConnectionDTO(connectionDO);

            // ?????????broker?????????
            if (brokerHostnameSet.contains(dto.getHostname()) || brokerHostnameSet.contains(dto.getIp())) {
                // ????????????????????????broker, ???????????????. brokerHostnameSet????????????????????????IP
                continue;
            }

            dtoList.add(dto);
            dtoListMap.put(deDuplicateKey, dtoList);
        }

        List<TopicConnection> dtoList = new ArrayList<>();
        for (Map.Entry<String, List<TopicConnection>> entry: dtoListMap.entrySet()) {
            dtoList.addAll(entry.getValue());
        }
        return dtoList;
    }

    private TopicConnection convert2TopicConnectionDTO(TopicConnectionDO connectionDO) {
        TopicConnection dto = new TopicConnection();
        dto.setClusterId(connectionDO.getClusterId());
        dto.setTopicName(connectionDO.getTopicName());
        switch (connectionDO.getType()) {
            case "produce": dto.setClientType("producer"); break;
            case "fetch": dto.setClientType("consumer"); break;
            default: dto.setClientType("");
        }
        dto.setAppId(connectionDO.getAppId());
        dto.setClientVersion(
                connectionDO.getClientVersion().equals(KafkaConstant.CLIENT_VERSION_CODE_UNKNOWN) ?
                        KafkaConstant.CLIENT_VERSION_NAME_UNKNOWN : connectionDO.getClientVersion()
        );
        dto.setIp(connectionDO.getIp());

        String hostName = connectionDO.getIp();
        try {
            InetAddress ia = InetAddress.getByAddress(getIpBytes(connectionDO.getIp()));
            hostName = ia.getHostName();
        } catch (Exception e) {
            LOGGER.error("get hostname failed. ip:{}.", connectionDO.getIp(), e);
        }
        dto.setHostname(hostName.replace(KafkaConstant.BROKER_HOST_NAME_SUFFIX, ""));
        return dto;
    }

    private byte[] getIpBytes(String ip) {
        String[] ipStr=ip.split("[.]");
        byte[] ipBytes=new byte[4];
        for (int i = 0; i < 4; i++) {
            int m=Integer.parseInt(ipStr[i]);
            byte b=(byte)(m&0xff);
            ipBytes[i]=b;
        }
        return ipBytes;
    }
}
