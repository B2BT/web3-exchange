<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
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
    ElMessage.success(t('register.success'))
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
      <div class="logo">{{ t('login.title') }}</div>
      <div class="subtitle">{{ t('register.subtitle') }}</div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item :label="t('register.username')" prop="username">
          <el-input v-model="form.username" :placeholder="t('register.usernamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('register.nickname')" prop="nickname">
          <el-input v-model="form.nickname" :placeholder="t('register.nicknamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('register.password')" prop="password">
          <el-input v-model="form.password" type="password" :placeholder="t('register.passwordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('register.confirmPassword')" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" :placeholder="t('register.confirmPasswordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('register.email')" prop="email">
          <el-input v-model="form.email" :placeholder="t('register.emailPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('register.phone')" prop="phone">
          <el-input v-model="form.phone" :placeholder="t('register.phonePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('register.captcha')" prop="captcha">
          <div class="captcha-row">
            <el-input v-model="form.captcha" :placeholder="t('register.captchaPlaceholder')" />
            <div class="captcha-img" :title="t('common.refresh')" @click="loadCaptcha">
              <el-image v-if="captchaIsImage && captchaImage" :src="captchaImage" fit="cover" />
              <span v-else class="captcha-text">{{ captchaImage || t('common.loading') }}</span>
            </div>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" class="reg-btn" :loading="loading" @click="handleRegister">
          {{ t('register.registerBtn') }}
        </el-button>
      </el-form>

      <div class="reg-footer">
        <router-link to="/login">{{ t('common.back') }} →</router-link>
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
  max-width: 100%;
  background: var(--glass-bg-strong);
  border-radius: var(--radius);
  padding: 32px 36px 24px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45), var(--glow-purple);
  animation: fadeInUp 0.5s ease;
}
@media (max-width: 767px) {
  .register-card {
    width: 100%;
    padding: 24px 20px 20px;
  }
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
