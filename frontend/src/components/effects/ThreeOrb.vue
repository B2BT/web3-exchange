<script setup lang="ts">
/**
 * 3D 旋转霓虹球体背景（Three.js）。
 * 登录页/市场页做真 3D 动效背景。动态 import 保证首屏构建体积不膨胀。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

const mountRef = ref<HTMLElement>()
let cleanup: (() => void) | null = null

async function initThree() {
  const THREE = await import('three')
  const mount = mountRef.value
  if (!mount) return

  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 100)
  camera.position.z = 5

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  mount.appendChild(renderer.domElement)

  const W = mount.clientWidth || 400
  const H = mount.clientHeight || 400
  renderer.setSize(W, H)
  camera.aspect = W / H
  camera.updateProjectionMatrix()

  // 线框球
  const sphere = new THREE.Mesh(
    new THREE.SphereGeometry(1.5, 24, 24),
    new THREE.MeshBasicMaterial({ wireframe: true, color: 0x22d3ee, transparent: true, opacity: 0.35 })
  )
  scene.add(sphere)

  // 内部发光小核
  const core = new THREE.Mesh(
    new THREE.SphereGeometry(0.45, 20, 20),
    new THREE.MeshBasicMaterial({ color: 0x8b5cf6, transparent: true, opacity: 0.55 })
  )
  scene.add(core)

  // 环绕圆环
  const torus = new THREE.Mesh(
    new THREE.TorusGeometry(2.3, 0.04, 16, 60),
    new THREE.MeshBasicMaterial({ color: 0x8b5cf6, transparent: true, opacity: 0.6 })
  )
  scene.add(torus)

  // 粒子星云
  const count = 400
  const pos = new Float32Array(count * 3)
  for (let i = 0; i < count; i++) {
    const r = 2.8 + Math.random() * 1.6
    const theta = Math.random() * Math.PI * 2
    const phi = Math.acos(2 * Math.random() - 1)
    pos[i * 3] = r * Math.sin(phi) * Math.cos(theta)
    pos[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta)
    pos[i * 3 + 2] = r * Math.cos(phi)
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  const particles = new THREE.Points(
    geo,
    new THREE.PointsMaterial({ color: 0xa78bfa, size: 0.03, transparent: true, opacity: 0.8 })
  )
  scene.add(particles)

  let raf = 0
  const animate = () => {
    sphere.rotation.x += 0.002
    sphere.rotation.y += 0.003
    torus.rotation.x += 0.004
    torus.rotation.y += 0.006
    particles.rotation.y += 0.001
    renderer.render(scene, camera)
    raf = requestAnimationFrame(animate)
  }
  animate()

  const onResize = () => {
    const w = mount.clientWidth || 400
    const h = mount.clientHeight || 400
    renderer.setSize(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
  }
  window.addEventListener('resize', onResize)

  cleanup = () => {
    cancelAnimationFrame(raf)
    window.removeEventListener('resize', onResize)
    sphere.geometry.dispose()
    core.geometry.dispose()
    torus.geometry.dispose()
    geo.dispose()
    renderer.dispose()
    if (renderer.domElement.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement)
  }
}

onMounted(initThree)
onBeforeUnmount(() => cleanup && cleanup())
</script>

<template>
  <div ref="mountRef" class="three-orb" aria-hidden="true"></div>
</template>

<style scoped>
.three-orb {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.three-orb :deep(canvas) {
  width: 100% !important;
  height: 100% !important;
}
</style>
