<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaId = ref('')
const captchaIsImage = ref(true)
const captchaLoading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  nickname: '',
  captcha: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '字母开头，仅含字母/数字/下划线', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{6,20}$/, message: '需含大小写字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (v === form.password ? cb() : cb(new Error('两次密码不一致'))),
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await authStore.fetchCaptcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.captchaImage
    captchaIsImage.value = !!data.captchaImage?.startsWith('data:image')
  } finally {
    captchaLoading.value = false
  }
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.register({
      username: form.username,
      password: form.password,
      email: form.email,
      phone: form.phone,
      nickname: form.nickname || undefined,
      captcha: form.captcha,
      captchaId: captchaId.value,
      source: 'web',
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    loadCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="register-page">
    <div class="register-card g-card">
      <div class="logo">Web3 交易所</div>
      <div class="subtitle">创建新账户</div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="字母开头，3-20位" clearable />
        </el-form-item>
        <el-form-item label="昵称（可选）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="含大小写字母和数字" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" clearable />
        </el-form-item>
        <el-form-item label="验证码" prop="captcha">
          <div class="captcha-row">
            <el-input v-model="form.captcha" placeholder="请输入验证码" />
            <div class="captcha-img" :title="'点击刷新'" @click="loadCaptcha">
              <el-image v-if="captchaIsImage && captchaImage" :src="captchaImage" fit="cover" />
              <span v-else class="captcha-text">{{ captchaImage || '加载中' }}</span>
            </div>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" class="reg-btn" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form>

      <div class="reg-footer">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  padding: 20px;
}
.register-card {
  width: 420px;
  background: var(--glass-bg-strong);
  border-radius: var(--radius);
  padding: 32px 36px 24px;
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
  margin: 6px 0 20px;
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
  font-size: 20px;
  font-weight: 700;
  color: var(--accent-cyan);
  font-style: italic;
}
.reg-btn {
  width: 100%;
}
.reg-footer {
  margin-top: 14px;
  text-align: right;
  font-size: 13px;
}
.reg-footer a {
  color: var(--accent-cyan);
  transition: color 0.2s;
}
.reg-footer a:hover {
  color: var(--accent-purple);
}
</style>
