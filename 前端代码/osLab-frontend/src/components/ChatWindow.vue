<template>
  <div class="chat-container">
    <!-- 聊天记录 -->
    <div class="chat-box">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="message"
        :class="{
          'user-message': message.sender === 'user',
          'bot-message': message.sender === 'bot',
        }"
      >
        <!-- 机器人头像（左侧） -->
        <div v-if="message.sender === 'bot'" class="avatar bot-avatar">
          <RobotOutlined />
        </div>

        <div class="bubble">{{ message.text }}</div>

        <!-- 用户头像（右侧） -->
        <div v-if="message.sender === 'user'" class="avatar user-avatar">
          <UserOutlined />
        </div>
      </div>
    </div>

    <!-- 输入框和发送按钮 -->
    <div class="chat-input">
      <input
        v-model="userMessage"
        @keyup.enter="sendMessage"
        placeholder="请输入问题..."
      />
      <button @click="sendMessage">发送</button>
    </div>
  </div>
</template>

<script>
import { ChatControllerService } from "../../generated";
import { RobotOutlined, UserOutlined } from "@ant-design/icons-vue";

export default {
  components: {
    RobotOutlined,
    UserOutlined,
  },
  data: function () {
    return {
      userMessage: "",
      messages: [
        {
          sender: "bot",
          text: "我是你的助手，你可以向我提问任何操作系统相关的问题",
        },
      ],
    };
  },
  methods: {
    async sendMessage() {
      if (this.userMessage.trim() === "") {
        this.$message.error("请输入问题后再发送哦！");
        return;
      }

      this.messages.push({ sender: "user", text: this.userMessage });
      const message = this.userMessage;
      this.userMessage = "";

      try {
        const response = await this.getAnswerFromBackend(message);
        this.messages.push({ sender: "bot", text: response });
      } catch (error) {
        this.messages.push({
          sender: "bot",
          text: "无法获取回答，请稍后再试。",
        });
      }
    },

    async getAnswerFromBackend(question) {
      const response = await ChatControllerService.askQuestionUsingPost(
        question
      );
      return response.data;
    },
  },
};
</script>

<style scoped>
.chat-container {
  width: 400px;
  height: 500px;
  border: 1px solid #ccc;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: linear-gradient(135deg, #f5f5f5, #e0e0e0);
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}

.chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  position: relative;
}

.chat-box::before {
  content: "聊天记录";
  position: absolute;
  top: -25px;
  left: 50%;
  transform: translateX(-50%);
  background-color: #f5f5f5;
  padding: 5px 10px;
  border-radius: 10px;
  font-size: 14px;
  color: #333;
  box-shadow: 0 0 5px rgba(0, 0, 0, 0.1);
}

.message {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-message {
  justify-content: flex-end;
}

.bot-message {
  justify-content: flex-start;
}

/* 头像样式 */
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-avatar {
  background-color: #165dff;
  color: white;
  order: 1; /* 用户头像放在最后（右侧） */
}

.bot-avatar {
  background-color: #e0e0e0;
  color: #333;
  order: -1; /* 机器人头像放在最前（左侧） */
}

.bubble {
  background-color: #f1f1f1;
  padding: 10px;
  border-radius: 20px;
  max-width: 70%;
  position: relative;
  transition: all 0.3s ease;
}

.user-message .bubble {
  background-color: #165dff;
  color: white;
  order: 0;
}

.user-message .bubble::before {
  right: 100%;
  border-right-color: #165dff;
}

.bot-message .bubble {
  background-color: #e0e0e0;
  order: 0;
}

.bot-message .bubble::before {
  left: 100%;
  border-left-color: #e0e0e0;
}

.chat-input {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  background-color: #f9f9f9;
  border-top: 1px solid #ccc;
}

input {
  width: 80%;
  padding: 5px;
  border-radius: 20px;
  border: 1px solid #ccc;
  box-shadow: 0 0 5px rgba(0, 0, 0, 0.1);
}

button {
  padding: 5px 10px;
  border-radius: 20px;
  background-color: #165dff;
  color: white;
  border: none;
  cursor: pointer;
  box-shadow: 0 0 5px rgba(0, 0, 0, 0.1);
}

button:hover {
  background-color: #165dff;
}
</style>
