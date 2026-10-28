<template>
  <section class="teaching-classes">
    <header class="classes-heading">
      <h1>班级管理</h1>
      <div class="toolbar">
        <span class="class-count">{{ classes.length }} 个班级</span>
        <a-button type="primary" @click="openCreate">
          <template #icon><icon-plus /></template>
          创建班级
        </a-button>
      </div>
    </header>

    <a-spin :loading="loading" class="classes-content">
      <a-empty v-if="!loading && classes.length === 0" description="暂无班级" />
      <div v-else class="class-list">
        <article v-for="item in classes" :key="item.id" class="class-row">
          <div class="class-main">
            <h2>{{ item.className }}</h2>
            <p>{{ item.description || "暂无班级说明" }}</p>
          </div>
          <div class="class-stats">
            <strong>{{ item.studentCount || 0 }}</strong>
            <span>名学生</span>
          </div>
          <div class="invite-code">
            <span>邀请码</span>
            <code>{{ item.classCode }}</code>
          </div>
          <div class="class-row-actions">
            <a-button type="text" @click="openMembers(item)">成员</a-button>
            <a-button type="text" @click="openEdit(item)">编辑</a-button>
            <a-button type="text" @click="rotateCode(item)"
              >重置邀请码</a-button
            >
            <a-button
              type="primary"
              status="success"
              @click="publishTask(item)"
            >
              发布任务
            </a-button>
            <a-button type="text" status="danger" @click="deleteClass(item)">
              删除
            </a-button>
          </div>
        </article>
      </div>
    </a-spin>

    <a-modal
      v-model:visible="editorVisible"
      :title="editingClass ? '编辑班级' : '创建班级'"
      :ok-loading="saving"
      @ok="saveClass"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="班级名称" required>
          <a-input v-model="form.className" maxlength="128" />
        </a-form-item>
        <a-form-item label="班级说明">
          <a-textarea
            v-model="form.description"
            :max-length="512"
            :auto-size="{ minRows: 2, maxRows: 4 }"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:visible="membersVisible"
      :width="520"
      :title="`${activeClass?.className || '班级'} · 成员`"
    >
      <div class="member-toolbar">
        <span>{{ members.length }} 名学生</span>
        <a-button size="small" @click="copyInviteCode">
          <template #icon><icon-copy /></template>
          复制邀请码
        </a-button>
      </div>
      <a-table
        row-key="userId"
        :columns="memberColumns"
        :data="members"
        :loading="membersLoading"
        :pagination="false"
      >
        <template #memberAction="{ record }">
          <a-button type="text" status="danger" @click="removeMember(record)">
            移除
          </a-button>
        </template>
      </a-table>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRouter } from "vue-router";
import {
  createManagedClass,
  deleteManagedClass,
  listManagedClasses,
  listManagedClassMembers,
  removeManagedClassMember,
  rotateManagedClassCode,
  updateManagedClass,
  type ClassMember,
  type ManagedClass,
} from "@/api/tasks";

const router = useRouter();
const classes = ref<ManagedClass[]>([]);
const members = ref<ClassMember[]>([]);
const loading = ref(false);
const membersLoading = ref(false);
const saving = ref(false);
const editorVisible = ref(false);
const membersVisible = ref(false);
const editingClass = ref<ManagedClass | null>(null);
const activeClass = ref<ManagedClass | null>(null);
const form = reactive({ className: "", description: "" });
const memberColumns = [
  { title: "姓名", dataIndex: "userName" },
  { title: "账号", dataIndex: "userAccount" },
  { title: "加入时间", dataIndex: "joinedAt" },
  { title: "操作", slotName: "memberAction", width: 76 },
];

const loadClasses = async () => {
  loading.value = true;
  try {
    const response = await listManagedClasses();
    if (response.data.code === 0) classes.value = response.data.data || [];
    else Message.error(response.data.message || "班级列表加载失败");
  } catch {
    Message.error("班级列表加载失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editingClass.value = null;
  form.className = "";
  form.description = "";
  editorVisible.value = true;
};

const openEdit = (item: ManagedClass) => {
  editingClass.value = item;
  form.className = item.className;
  form.description = item.description || "";
  editorVisible.value = true;
};

const saveClass = async () => {
  const className = form.className.trim();
  if (!className || saving.value) return;
  saving.value = true;
  try {
    const response = editingClass.value
      ? await updateManagedClass(editingClass.value.id, {
          className,
          description: form.description.trim(),
        })
      : await createManagedClass({
          className,
          description: form.description.trim(),
        });
    if (response.data.code === 0) {
      editorVisible.value = false;
      Message.success(editingClass.value ? "班级已更新" : "班级已创建");
      await loadClasses();
    } else Message.error(response.data.message || "保存班级失败");
  } catch {
    Message.error("保存班级失败，请稍后重试");
  } finally {
    saving.value = false;
  }
};

const rotateCode = async (item: ManagedClass) => {
  if (
    !window.confirm(
      `重置“${item.className}”的邀请码后，旧邀请码将立即失效。继续吗？`
    )
  )
    return;
  try {
    const response = await rotateManagedClassCode(item.id);
    if (response.data.code === 0) {
      Message.success(`新邀请码：${response.data.data.classCode}`);
      await loadClasses();
    } else Message.error(response.data.message || "重置邀请码失败");
  } catch {
    Message.error("重置邀请码失败，请稍后重试");
  }
};

const deleteClass = async (item: ManagedClass) => {
  if (!window.confirm(`删除“${item.className}”并解除全部成员关系？`)) return;
  try {
    const response = await deleteManagedClass(item.id);
    if (response.data.code === 0) {
      Message.success("班级已删除");
      await loadClasses();
    } else Message.error(response.data.message || "删除班级失败");
  } catch {
    Message.error("删除班级失败，请稍后重试");
  }
};

const openMembers = async (item: ManagedClass) => {
  activeClass.value = item;
  membersVisible.value = true;
  membersLoading.value = true;
  try {
    const response = await listManagedClassMembers(item.id);
    if (response.data.code === 0) members.value = response.data.data || [];
    else Message.error(response.data.message || "成员列表加载失败");
  } catch {
    Message.error("成员列表加载失败，请稍后重试");
  } finally {
    membersLoading.value = false;
  }
};

const removeMember = async (member: ClassMember) => {
  const item = activeClass.value;
  if (!item || !window.confirm(`将 ${member.userName} 移出班级？`)) return;
  try {
    const response = await removeManagedClassMember(item.id, member.userId);
    if (response.data.code === 0) {
      members.value = members.value.filter(
        (row) => String(row.userId) !== String(member.userId)
      );
      Message.success("成员已移除");
      await loadClasses();
    } else Message.error(response.data.message || "移除成员失败");
  } catch {
    Message.error("移除成员失败，请稍后重试");
  }
};

const copyInviteCode = async () => {
  const code = activeClass.value?.classCode;
  if (!code) return;
  try {
    await navigator.clipboard.writeText(code);
    Message.success("邀请码已复制");
  } catch {
    Message.info(`邀请码：${code}`);
  }
};

const publishTask = (item: ManagedClass) => {
  void router.push({
    path: "/teaching/tasks/new",
    query: { className: item.className },
  });
};

onMounted(loadClasses);
</script>

<style scoped>
.teaching-classes {
  width: 100%;
  padding-bottom: 32px;
}
.classes-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.classes-heading h1 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 24px;
  font-weight: 650;
}
.toolbar,
.member-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.class-count,
.member-toolbar > span {
  color: var(--oslab-muted-strong);
  font-size: 13px;
}
.classes-content {
  display: block;
  min-height: 140px;
}
.class-list {
  border-top: 1px solid var(--oslab-line);
}
.class-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto minmax(145px, auto);
  gap: 16px;
  align-items: center;
  padding: 18px 4px;
  border-bottom: 1px solid var(--oslab-line);
}
.class-main h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 16px;
}
.class-main p {
  margin: 5px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.class-stats {
  display: flex;
  align-items: baseline;
  gap: 5px;
  color: var(--oslab-muted-strong);
  font-size: 12px;
  white-space: nowrap;
}
.class-stats strong {
  color: var(--oslab-ink);
  font-size: 18px;
}
.invite-code {
  display: grid;
  gap: 4px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.invite-code code {
  color: var(--oslab-ink);
  font-size: 13px;
}
.class-row-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: -8px;
}
@media (max-width: 760px) {
  .classes-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .class-row {
    grid-template-columns: 1fr auto;
  }
  .class-row-actions {
    justify-content: flex-start;
  }
}
</style>
