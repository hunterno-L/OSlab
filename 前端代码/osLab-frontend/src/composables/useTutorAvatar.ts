import { computed, ref } from "vue";

export type TutorGender = "female" | "male";

export const TUTOR_GENDERS: TutorGender[] = ["female", "male"];

export const TUTOR_GENDER_LABELS: Record<TutorGender, string> = {
  female: "女",
  male: "男",
};

const DEFAULT_GENDER: TutorGender = "female";
const STORAGE_KEY = "oslab-ai-tutor-gender";
const BASE = process.env.BASE_URL;

export const TUTOR_AVATARS: Record<
  TutorGender,
  { label: string; png: string; webp: string }
> = {
  female: {
    label: "女",
    png: `${BASE}ai-tutor-female.png`,
    webp: `${BASE}ai-tutor-female.webp`,
  },
  male: {
    label: "男",
    png: `${BASE}ai-tutor-male.png`,
    webp: `${BASE}ai-tutor-male.webp`,
  },
};

function readGender(): TutorGender {
  try {
    return localStorage.getItem(STORAGE_KEY) === "male"
      ? "male"
      : DEFAULT_GENDER;
  } catch {
    return DEFAULT_GENDER;
  }
}

export const tutorGender = ref<TutorGender>(readGender());

export function setTutorGender(gender: TutorGender) {
  tutorGender.value = gender;
  try {
    localStorage.setItem(STORAGE_KEY, gender);
  } catch {
    // Presentation preference only; keep the in-memory choice.
  }
}

export const tutorAvatar = computed(() => TUTOR_AVATARS[tutorGender.value]);
