package com.fana.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fana.config.ResponseResult;
import com.fana.config.Status;
import com.fana.entry.pojo.TbUser;
import com.fana.entry.pojo.TbWebUser;
import com.fana.entry.vo.IPageVo;
import com.fana.entry.vo.LoginVo;
import com.fana.entry.vo.WebUserVo;
import com.fana.exception.CustomException;
import com.fana.mapper.TbUserMapper;
import com.fana.mapper.TbWebUserMapper;
import com.fana.service.ITbWebUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fana.utils.FileUtils;
import com.fana.utils.LocalDateTimeFormatter;
import com.fana.utils.LogUtil;
import com.fana.utils.MD5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author astupidcoder
 * @since 2022-05-25
 */
@Slf4j
@Service
public class TbWebUserServiceImpl extends ServiceImpl<TbWebUserMapper, TbWebUser> implements ITbWebUserService {

    @Resource
    private TbWebUserMapper webUserMapper;
    @Resource
    private TbUserMapper userMapper;
    @Resource
    FileUtils fileUtils;

    @Override
    public ResponseResult login(LoginVo vo) {

        TbWebUser tbWebUser = webUserMapper.selectOne(new QueryWrapper<TbWebUser>()
                .lambda().eq(TbWebUser::getUsername, vo.getUsername()));
        if (ObjectUtil.isEmpty(tbWebUser)) {
            log.info("用户不存在...");
            throw new CustomException(Status.USER_VOID.code, Status.USER_VOID.message);
        }
        if (!MD5Util.inputPassToDbPass(vo.getPassword()).equals(tbWebUser.getPassword())) {
            log.info("密码错误...");
            throw new CustomException(Status.PASSWORD_ERROR.code, Status.PASSWORD_ERROR.message);
        }
        LoginVo response = LoginVo.builder()
                .username(tbWebUser.getUsername())
                .id(tbWebUser.getId().toString())
                .roles(tbWebUser.getRoleId())
                .build();
        return ResponseResult.success(response);
    }

    @Override
    public ResponseResult getList(WebUserVo vo) {
        LogUtil.addInfoLog("获取用户列表", "/user/list", JSON.toJSON(vo));
        QueryWrapper<TbWebUser> queryWrapper = new QueryWrapper<>();
        QueryWrapper<TbUser> query = new QueryWrapper<>();
        if (vo.getPlatform().equals(0))//web平台
        {
            if (StrUtil.isNotBlank(vo.getSearch()))
                queryWrapper.like("username", vo.getSearch()).or().like("role_id", vo.getSearch());
            IPage<TbWebUser> page = new Page<>(vo.getPageNum(), vo.getPageSize());
            IPage<TbWebUser> iPage = webUserMapper.selectPage(page, queryWrapper);
//            iPage.getRecords().forEach(webuser->{
//                LocalDateTimeFormatter.getLocalDateTimeFormatter(webuser.)
//            });
            IPageVo build = IPageVo.builder().total(iPage.getTotal()).pageSize(iPage.getSize()).pageNum(iPage.getCurrent()).list(iPage.getRecords()).build();
            return ResponseResult.success(build);
        }
        if (vo.getPlatform().equals(1))//app平台
        {
            if (StrUtil.isNotBlank(vo.getSearch()))
                queryWrapper.like("email", vo.getSearch()).or().like("last_name", vo.getSearch());
            IPage<TbUser> page = new Page<>(vo.getPageNum(), vo.getPageSize());
            IPage<TbUser> iPage = userMapper.selectPage(page, query);
            IPageVo build = IPageVo.builder().total(iPage.getTotal()).pageSize(iPage.getSize()).pageNum(iPage.getCurrent()).list(iPage.getRecords()).build();
            return ResponseResult.success(build);
        }
        return ResponseResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult updateUser(WebUserVo vo) {
        LogUtil.addInfoLog("修改用户信息", "/user/update", JSON.toJSON(vo));

        if (vo.getPlatform().equals(0)) {//web
            TbWebUser webUser = TbWebUser.builder().build();
            TbWebUser tbWebUser = webUserMapper.selectById(vo.getId());
            if (!MD5Util.inputPassToDbPass(vo.getPassword()).equals(tbWebUser.getPassword())) {
                webUser = TbWebUser.builder()
                        .id(vo.getId())
                        .username(vo.getUsername())
                        .password(MD5Util.inputPassToDbPass(vo.getPassword()))
                        .roleId(vo.getRoleId())
                        .isDelete(vo.getIsDelete())
                        .build();
            } else {
                webUser = TbWebUser.builder().id(vo.getId()).username(vo.getUsername()).roleId(vo.getRoleId()).isDelete(vo.getIsDelete()).build();
            }
            try {
                webUserMapper.updateById(webUser);
            } catch (Exception e) {
                LogUtil.addErrorLog("修改用户信息error", "/user/update", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }
        if (vo.getPlatform().equals(1)) {//app
            TbUser user = TbUser.builder().build();
            TbUser tbUser = userMapper.selectById(vo.getId());
            if (!MD5Util.inputPassToDbPass(vo.getPassword()).equals(tbUser.getPassword())) {
                user = user.builder()
                        .id(vo.getId())
                        .email(vo.getUsername())
                        .password(MD5Util.inputPassToDbPass(vo.getPassword()))
                        .isDelete(vo.getIsDelete())
                        .build();
            } else {
                user = user.builder().id(vo.getId()).email(vo.getUsername()).isDelete(vo.getIsDelete()).build();
            }
            try {
                userMapper.updateById(user);
            } catch (Exception e) {
                LogUtil.addErrorLog("修改用户信息error", "/user/update", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }

        return ResponseResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult selectUser(WebUserVo vo) {
        LogUtil.addInfoLog("获取用户信息详情", "/user/select", JSON.toJSON(vo));
        if (vo.getPlatform().equals(0)) {//web
            TbWebUser tbWebUser = webUserMapper.selectById(vo.getId());
            return ResponseResult.success(tbWebUser);
        }
        if (vo.getPlatform().equals(1)) {//app
            TbUser tbUser = userMapper.selectById(vo.getId());
            return ResponseResult.success(tbUser);
        }
        return ResponseResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult deleteUser(WebUserVo vo) {
        LogUtil.addInfoLog("delete用户信息详情", "/user/delete", JSON.toJSON(vo));
        UpdateWrapper queryWrapper = new UpdateWrapper();
        if (vo.getPlatform().equals(0)) {//web
            try {
                queryWrapper.eq("id",vo.getId());
                queryWrapper.set("id",vo.getId());
//                webUserMapper.updateById(TbWebUser.builder().id(vo.getId()).isDelete(1).build());
                webUserMapper.update(TbWebUser.builder().id(vo.getId()).isDelete(1).build(),queryWrapper);
            } catch (Exception e) {
                LogUtil.addErrorLog("delete用户信息详情error", "/user/delete", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }
        if (vo.getPlatform().equals(1)) {//app
            try {
                userMapper.updateById(TbUser.builder().id(vo.getId()).isDelete(1).build());
            } catch (Exception e) {
                LogUtil.addErrorLog("delete用户信息详情error", "/user/delete", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }
        return ResponseResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult addUser(WebUserVo vo) {
        if (vo.getPlatform().equals(0)) {//web
            try {
                webUserMapper.insert(TbWebUser.builder()
                        .username(vo.getUsername())
                        .password(MD5Util.inputPassToDbPass(vo.getPassword()))
                        .build());
            } catch (Exception e) {
                LogUtil.addErrorLog("add用户信息详情error", "/user/add", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }
        if (vo.getPlatform().equals(1)) {//app
            try {
                userMapper.insert(TbUser.builder()
                        .email(vo.getUsername())
                        .password(MD5Util.inputPassToDbPass(vo.getPassword()))
                        .firstName(vo.getFirstName())
                        .lastName(vo.getLastName())
                        .build());
            } catch (Exception e) {
                LogUtil.addErrorLog("add用户信息详情error", "/user/add", e.getMessage());
                throw new CustomException(201, e.getMessage());
            }
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult uploadUserImage(MultipartFile file) {
        if(file == null){
            return new ResponseResult(Status.PARAMETER_ERROR.code, "The file did not fill in  ");
        }
        String upload = null;
        try {
            upload = fileUtils.upload(file,"user");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(StringUtils.isEmpty(upload)){
            throw new CustomException(Status.IMAGE_UPLOAD_FAILED.getCode(),Status.IMAGE_UPLOAD_FAILED.getMessage());
        }
        return ResponseResult.success(upload);
    }


}
