<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import ThreeOrb from '@/components/effects/ThreeOrb.vue'
import ParticleBackground from '@/components/effects/ParticleBackground.vue'

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
    <!-- 3D 旋转霓虹球体背景 -->
    <ThreeOrb />
    <ParticleBackground />
    <div class="login-card g-card tilt3d neon-edge">
      <div class="logo floaty">Web3 交易所</div>
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
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 透明底，让 App 装饰层（网格/光晕）透出 */
  background: transparent;
  padding: 20px;
}
.login-card {
  position: relative;
  z-index: 2;
  width: 400px;
  background: var(--glass-bg-strong);
  border-radius: var(--radius);
  padding: 40px 36px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45), var(--glow-purple);
  animation: fadeInUp 0.5s ease;
}
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
.logo {
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  background: var(--accent-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 16px rgba(124, 58, 237, 0.5));
}
.subtitle {
  text-align: center;
  color: var(--text-secondary);
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
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.55);
  flex-shrink: 0;
  transition: border-color 0.2s;
}
.captcha-img:hover {
  border-color: var(--glass-hover-border);
}
.captcha-img :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.captcha-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--accent-cyan);
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
  color: var(--accent-cyan);
  transition: color 0.2s;
}
.login-footer a:hover {
  color: var(--accent-purple);
}
</style>
