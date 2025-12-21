package com.web3.exchange.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "用户信息响应")
public class UserInfoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "姓名", example = "管理员")
    private String realName;

    @Schema(description = "昵称", example = "超级管理员")
    private String nickname;

    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "性别 0:未知 1:男 2:女", example = "1")
    private Integer gender;

    @Schema(description = "性别显示文本", example = "男")
    private String genderText;

    @Schema(description = "生日")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @Schema(description = "年龄", example = "25")
    private Integer age;

    @Schema(description = "状态 0:禁用 1:启用", example = "1")
    private Integer status;

    @Schema(description = "状态显示文本", example = "启用")
    private String statusText;

    @Schema(description = "部门ID", example = "100")
    private Long deptId;

    @Schema(description = "部门名称", example = "技术部")
    private String deptName;

    @Schema(description = "岗位ID", example = "1")
    private Long postId;

    @Schema(description = "岗位名称", example = "Java开发")
    private String postName;

    @Schema(description = "角色列表")
    private List<String> roles;

    @Schema(description = "权限列表")
    private List<String> permissions;

    @Schema(description = "权限标识列表")
    private List<String> authorities;

    @Schema(description = "最后登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP", example = "192.168.1.1")
    private String lastLoginIp;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "是否首次登录", example = "true")
    private Boolean firstLogin = false;

    @Schema(description = "是否需要修改密码", example = "false")
    private Boolean needChangePassword = false;

    @Schema(description = "密码过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime passwordExpireTime;

    @Schema(description = "扩展信息")
    private String extendInfo;

    // 计算年龄
    public Integer getAge() {
        if (birthday == null) {
            return null;
        }
        LocalDate now = LocalDate.now();
        int age = now.getYear() - birthday.getYear();
        if (now.getMonthValue() < birthday.getMonthValue() ||
                (now.getMonthValue() == birthday.getMonthValue() &&
                        now.getDayOfMonth() < birthday.getDayOfMonth())) {
            age--;
        }
        return age;
    }

    // 设置显示文本
    public void setGenderText(Integer gender) {
        if (gender == null) {
            this.genderText = "未知";
            return;
        }
        switch (gender) {
            case 1: this.genderText = "男"; break;
            case 2: this.genderText = "女"; break;
            default: this.genderText = "未知";
        }
    }

    public void setStatusText(Integer status) {
        if (status == null) {
            this.statusText = "未知";
            return;
        }
        this.statusText = status == 1 ? "启用" : "禁用";
    }

    // Builder 方法
    public static UserInfoResponse simple(Long id, String username, String realName) {
        return UserInfoResponse.builder()
                .id(id)
                .username(username)
                .realName(realName)
                .build();
    }
}