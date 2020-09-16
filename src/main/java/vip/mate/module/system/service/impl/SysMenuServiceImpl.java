package vip.mate.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import vip.mate.core.common.constant.MateConstant;
import vip.mate.core.util.TreeUtil;
import vip.mate.core.web.util.CollectionUtil;
import vip.mate.module.system.entity.SysMenu;
import vip.mate.module.system.mapper.SysMenuMapper;
import vip.mate.module.system.poi.SysMenuPOI;
import vip.mate.module.system.service.ISysMenuService;
import vip.mate.module.system.vo.SysMenuVO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 菜单权限表 服务实现类
 * </p>
 *
 * @author xuzf
 * @since 2020-06-18
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    @Override
    public List<SysMenuVO> routes(String roleId) {
        //1. 获取用户的菜单列表，待扩展
        List<SysMenu> menus = this.baseMapper.routes(roleId);
        //2. 生成菜单树
        return TreeUtil.list2Tree(menus, MateConstant.TREE_ROOT);
    }

    @Override
    public List<SysMenu> searchList(Map<String, Object> search) {
        String keyword = String.valueOf(search.get("keyword"));
        String startDate = String.valueOf(search.get("startDate"));
        String endDate = String.valueOf(search.get("endDate"));
        LambdaQueryWrapper<SysMenu> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(startDate) && !startDate.equals("null")) {
            lambdaQueryWrapper.between(SysMenu::getCreateTime, startDate, endDate);
        }
        if (StringUtils.isNotBlank(keyword) && !keyword.equals("null")) {
            lambdaQueryWrapper.like(SysMenu::getName, keyword);
            lambdaQueryWrapper.or();
            lambdaQueryWrapper.like(SysMenu::getId, keyword);
        }
        lambdaQueryWrapper.orderByAsc(SysMenu::getSort);
        return this.baseMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public boolean saveAll(SysMenu sysMenu) {
        if (sysMenu.getType().equals("0")) {
            sysMenu.setParentId(-1L);
        }
        return saveOrUpdate(sysMenu);
    }

    @Override
    public boolean status(String ids, String status) {
        Collection<? extends Serializable> collection = CollectionUtil.stringToCollection(ids);

        for (Object id: collection){
            SysMenu sysMenu = this.baseMapper.selectById(CollectionUtil.objectToLong(id, 0L));
            sysMenu.setStatus(status);
            this.baseMapper.updateById(sysMenu);
        }
        return true;
    }

    @Override
    public List<SysMenuPOI> export() {
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getIsDeleted, 0);
        queryWrapper.orderByAsc(SysMenu::getId);
        List<SysMenu> sysMenus = this.baseMapper.selectList(queryWrapper);
        return sysMenus.stream().map(sysMenu -> {
            SysMenuPOI sysMenuPOI = new SysMenuPOI();
            BeanUtils.copyProperties(sysMenu, sysMenuPOI);
            return sysMenuPOI;
        }).collect(Collectors.toList());
    }
}
