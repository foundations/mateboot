package vip.mate.module.component.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.core.common.api.Result;
import vip.mate.core.common.constant.ComponentConstant;
import vip.mate.core.common.util.StringPool;
import vip.mate.core.common.util.StringUtil;
import vip.mate.core.database.entity.Search;
import vip.mate.core.oss.core.OssTemplate;
import vip.mate.core.oss.props.OssProperties;
import vip.mate.core.web.util.OssUtil;
import vip.mate.module.component.entity.SysAttachment;
import vip.mate.module.component.mapper.SysAttachmentMapper;
import vip.mate.module.component.service.ISysAttachmentService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>
 * 附件表 服务实现类
 * </p>
 *
 * @author pangu
 * @since 2020-08-09
 */
@Service
@AllArgsConstructor
public class SysAttachmentServiceImpl extends ServiceImpl<SysAttachmentMapper, SysAttachment> implements ISysAttachmentService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final OssTemplate ossTemplate;

    /**
     * 分页查询
     * @param page 分页组件
     * @param search 搜索内容
     * @return 分页列表
     */
    @Override
    public IPage<SysAttachment> listPage(Page page, Search search) {
        LambdaQueryWrapper<SysAttachment> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotBlank(search.getStartDate())) {
            queryWrapper.between(SysAttachment::getCreateTime, search.getStartDate(), search.getEndDate());
        }
        if (StringUtil.isNotBlank(search.getKeyword())) {
            queryWrapper.like(SysAttachment::getName, search.getKeyword());
            queryWrapper.or();
            queryWrapper.like(SysAttachment::getId, search.getKeyword());
        }
        return this.baseMapper.selectPage(page, queryWrapper);
    }

    /**
     * 上传文件
     * @param file 文件体
     * @return boolean
     */
    @Override
    public Result<?> upload(MultipartFile file) {

        OssProperties ossProperties = getOssProperties();
        String fileName = UUID.randomUUID().toString().replace("-", "")
                + StringPool.DOT + FilenameUtils.getExtension(file.getOriginalFilename());
        Map<String, String> uMap = new HashMap<>(4);
        try {
            //上传文件
            assert ossProperties != null;
            ossTemplate.putObject(ossProperties.getBucketName(), fileName, file.getInputStream(), file.getSize(), file.getContentType());
            //生成URL
            String url = "https://" + ossProperties.getCustomDomain() + StringPool.SLASH + fileName;
            //自定义返回报文

            uMap.put("bucketName", ossProperties.getBucketName());
            uMap.put("fileName", fileName);
            uMap.put("url", url);
            //上传成功后记录入库
            this.attachmentLog(file, url, fileName);
        } catch (Exception e) {
            log.error("上传失败", e);
            return Result.fail(e.getMessage());
        }
        return Result.data(uMap);
    }

    @Override
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        OssProperties ossProperties = getOssProperties();
        SysAttachment sysAttachment = this.getById(id);
        ossTemplate.removeObject(ossProperties.getBucketName(), sysAttachment.getFileName());
        return this.removeById(id);
    }

    /**
     * 将上传成功的文件记录入库
     * @param file　文件
     * @param url　返回的URL
     * @return boolean
     */
    public boolean attachmentLog(MultipartFile file, String url, String fileName) {
        SysAttachment sysAttachment = new SysAttachment();
        String original = file.getOriginalFilename();
        String originalName = FilenameUtils.getName(original);
        sysAttachment.setName(originalName);
        sysAttachment.setUrl(url);
        sysAttachment.setFileName(fileName);
        sysAttachment.setSize(file.getSize());
        sysAttachment.setType(OssUtil.getFileType(original));
        return this.save(sysAttachment);
    }

    /**
     * 从redis里读取OssProperties
     * @return OssProperties
     */
    public OssProperties getOssProperties(){
        return (OssProperties) redisTemplate.opsForValue().get(ComponentConstant.OSS_DEFAULT);
    }
}
