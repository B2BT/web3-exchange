<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaLoading = ref(false)
const captchaImage = ref('')
const captchaId = ref('')
/** 判断验证码是图片还是算式文本 */
const captchaIsImage = ref(true)

const form = reactive({
  username: '',
  password: '',
  captcha: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20', trigger: 'blur' },
  ],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await authStore.fetchCaptcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.captchaImage
    // 以 data:image 开头为图片；否则（测试环境）为算式文本
    captchaIsImage.value = !!data.captchaImage?.startsWith('data:image')
    // 测试环境校验码可能直接回显，方便调试
    if (data.captchaText) {
      console.info('[captcha] 测试环境验证码答案 =', data.captchaText)
    }
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login({
      username: form.username,
      password: form.password,
      captcha: form.captcha,
      captchaId: captchaId.value,
      device: 'web',
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/market'
    router.push(redirect)
  } catch {
    // 登录失败时刷新验证码
    loadCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo">Web3 交易所</div>
      <div class="subtitle">去中心化数字资产交易平台</div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="验证码" prop="captcha">
          <div class="captcha-row">
            <el-input v-model="form.captcha" placeholder="请输入验证码" />
            <div
              class="captcha-img"
              :title="'点击刷新验证码'"
              @click="loadCaptcha"
            >
              <el-image
                v-if="captchaIsImage && captchaImage"
                :src="captchaImage"
                fit="cover"
              />
              <span v-else class="captcha-text">{{ captchaImage || '加载中' }}</span>
            </div>
          </div>
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-footer">
        <router-link to="/register">没有账号？立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1c2c 0%, #2b2f4a 50%, #3a3d63 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 36px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
}
.logo {
  font-size: 26px;
  font-weight: 700;
  color: #1a1c2c;
  text-align: center;
}
.subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin: 6px 0 24px;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  width: 110px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  flex-shrink: 0;
}
.captcha-img :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.captcha-text {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
  font-style: italic;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
.login-footer {
  margin-top: 16px;
  text-align: right;
  font-size: 13px;
}
.login-footer a {
  color: #409eff;
}
</style>
