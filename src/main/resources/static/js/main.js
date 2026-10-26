/**
 * FitFlow – Main JavaScript
 * Handles: live clock, sidebar, flash auto-dismiss, toast notifications,
 *          stat counters, confirmation dialogs, form feedback,
 *          AI assistant (demo mode), interactive demo page
 */

/* ================================================================
   UTILITY
   ================================================================ */

// Live clock
function updateClock() {
  const el = document.getElementById('topbarTime');
  if (!el) return;
  const now = new Date();
  el.textContent = now.toLocaleString('en-IN', {
    weekday: 'short', day: '2-digit', month: 'short',
    hour: '2-digit', minute: '2-digit', hour12: true
  });
}
updateClock();
setInterval(updateClock, 30000);

/* ================================================================
   TOAST NOTIFICATIONS
   ================================================================ */

function createToastContainer() {
  let c = document.getElementById('toast-container');
  if (!c) {
    c = document.createElement('div');
    c.id = 'toast-container';
    document.body.appendChild(c);
  }
  return c;
}

function showToast(message, type = 'info', duration = 4000) {
  const container = createToastContainer();
  const icons = { success: '✅', error: '❌', info: 'ℹ️', warning: '⚠️' };
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span class="toast-icon">${icons[type] || 'ℹ️'}</span><span>${message}</span>`;
  toast.onclick = () => dismissToast(toast);
  container.appendChild(toast);
  setTimeout(() => dismissToast(toast), duration);
}

function dismissToast(toast) {
  toast.style.transition = 'all 0.3s ease';
  toast.style.opacity = '0';
  toast.style.transform = 'translateX(100%)';
  setTimeout(() => toast.remove(), 300);
}

/* ================================================================
   FLASH AUTO-DISMISS + UPGRADE TO TOAST
   ================================================================ */

document.addEventListener('DOMContentLoaded', () => {
  const flashes = document.querySelectorAll('.flash');
  flashes.forEach(flash => {
    setTimeout(() => {
      flash.style.transition = 'opacity 0.5s ease';
      flash.style.opacity = '0';
      setTimeout(() => flash.remove(), 500);
    }, 5000);
  });
});

/* ================================================================
   SIDEBAR TOGGLE (desktop + mobile)
   ================================================================ */

document.addEventListener('DOMContentLoaded', () => {
  const sidebar = document.getElementById('sidebar');
  const toggle = document.querySelector('.sidebar-toggle');
  if (!sidebar || !toggle) return;

  // Create overlay for mobile
  let overlay = document.querySelector('.sidebar-overlay');
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.className = 'sidebar-overlay';
    document.body.appendChild(overlay);
  }

  toggle.addEventListener('click', () => {
    const isMobile = window.innerWidth <= 900;
    if (isMobile) {
      sidebar.classList.toggle('mobile-open');
      overlay.classList.toggle('active');
    } else {
      sidebar.classList.toggle('collapsed');
    }
  });

  overlay.addEventListener('click', () => {
    sidebar.classList.remove('mobile-open');
    overlay.classList.remove('active');
  });

  // Highlight active nav link
  const links = document.querySelectorAll('.nav-link');
  const current = window.location.pathname;
  links.forEach(link => {
    const href = link.getAttribute('href');
    if (href && (href === current || (href !== '/' && current.startsWith(href)))) {
      link.classList.add('active');
    }
  });
});

/* ================================================================
   ANIMATED STAT COUNTERS
   ================================================================ */

function animateCounter(el, target, duration = 900) {
  const start = performance.now();
  const update = (now) => {
    const elapsed = now - start;
    const progress = Math.min(elapsed / duration, 1);
    const ease = 1 - Math.pow(1 - progress, 3); // cubic ease-out
    el.textContent = Math.round(ease * target);
    if (progress < 1) requestAnimationFrame(update);
    else el.textContent = target;
  };
  requestAnimationFrame(update);
}

document.addEventListener('DOMContentLoaded', () => {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const el = entry.target;
        const target = parseInt(el.getAttribute('data-target') || el.textContent, 10);
        if (!isNaN(target)) {
          el.setAttribute('data-target', target);
          animateCounter(el, target);
        }
        observer.unobserve(el);
      }
    });
  }, { threshold: 0.3 });

  document.querySelectorAll('.stat-number, .diff-count').forEach(el => {
    observer.observe(el);
  });

  // Animate difficulty bars
  document.querySelectorAll('.diff-bar-fill').forEach(bar => {
    setTimeout(() => {
      bar.style.width = bar.getAttribute('data-width') || '0%';
    }, 200);
  });
});

/* ================================================================
   CONFIRMATION MODAL (replaces browser confirm())
   ================================================================ */

function createConfirmModal(message, title = 'Are you sure?', icon = '🗑️') {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay active';
    overlay.innerHTML = `
      <div class="modal-box" role="dialog" aria-modal="true">
        <span class="modal-icon">${icon}</span>
        <h2 class="modal-title">${title}</h2>
        <p class="modal-text">${message}</p>
        <div class="modal-actions">
          <button class="btn btn-secondary" id="modalCancel">Cancel</button>
          <button class="btn btn-danger" id="modalConfirm">Delete</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);
    overlay.querySelector('#modalCancel').onclick = () => { overlay.remove(); resolve(false); };
    overlay.querySelector('#modalConfirm').onclick = () => { overlay.remove(); resolve(true); };
    overlay.addEventListener('click', e => { if (e.target === overlay) { overlay.remove(); resolve(false); } });
  });
}

// Intercept delete forms
document.addEventListener('DOMContentLoaded', () => {
  const deleteForms = document.querySelectorAll('.delete-form');
  deleteForms.forEach(form => {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const confirmed = await createConfirmModal(
        'This action cannot be undone. The workout plan will be permanently removed.',
        'Delete Workout Plan?',
        '🗑️'
      );
      if (confirmed) form.submit();
    });
  });
});

/* ================================================================
   FORM VALIDATION FEEDBACK
   ================================================================ */

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('workoutForm');
  if (!form) return;

  form.querySelectorAll('.form-input').forEach(input => {
    input.addEventListener('blur', () => validateField(input));
    input.addEventListener('input', () => {
      if (input.classList.contains('is-invalid')) validateField(input);
    });
  });

  function validateField(input) {
    const hasError = !input.checkValidity() || (input.required && !input.value.trim());
    input.classList.toggle('is-invalid', hasError);
  }
});

/* ================================================================
   AI ASSISTANT (Demo Mode – no external API)
   ================================================================ */

const AI_RESPONSES = {
  greeting: [
    "👋 Hi! I'm FitBot, your AI workout assistant. I'm running in **Demo Mode** — responses are pre-defined for demonstration purposes. Ask me about workout plans, exercise selection, or fitness goals!",
  ],
  workout_plan: {
    triggers: ['workout', 'plan', 'create', 'build', 'design', 'programme', 'program'],
    response: `Here's a sample **Intermediate Full-Body Plan** I can suggest:`,
    card: {
      title: '💪 Full-Body Strength Circuit (45 min)',
      items: [
        '• Warm-up: 5 min light cardio',
        '• Squats: 4 × 12 reps @ 60% 1RM',
        '• Bench Press: 4 × 10 reps',
        '• Deadlift: 3 × 8 reps @ 70% 1RM',
        '• Pull-ups: 3 × max reps',
        '• Plank: 3 × 45 seconds',
        '• Cool-down: 5 min stretching',
      ]
    },
    followup: '*Note: This is a demo suggestion. Save it to your workout plans to make it official!*'
  },
  beginner: {
    triggers: ['beginner', 'start', 'new', 'first time', 'newbie', 'starting out'],
    response: `For beginners, I recommend starting with **3 days/week** full-body workouts. Here's a starter routine:`,
    card: {
      title: '🌱 Beginner Starter Plan (30 min)',
      items: [
        '• Bodyweight squats: 3 × 15',
        '• Push-ups (modified if needed): 3 × 10',
        '• Dumbbell rows: 3 × 12 per arm',
        '• Glute bridges: 3 × 15',
        '• Bird-dog: 3 × 10 per side',
        '• Rest 90 seconds between sets',
      ]
    },
    followup: '*Progression: Add 5% weight or 2 reps each week. Listen to your body!*'
  },
  advanced: {
    triggers: ['advanced', 'elite', 'hardcore', 'intense', 'pro'],
    response: `For advanced athletes, periodization is key. Here's an advanced split:`,
    card: {
      title: '🔥 Advanced 5-Day Split',
      items: [
        '• Monday: Push (Chest, Shoulders, Triceps)',
        '• Tuesday: Pull (Back, Biceps, Rear Delts)',
        '• Wednesday: Legs (Quads, Hamstrings, Calves)',
        '• Thursday: Rest/Active Recovery',
        '• Friday: Upper Body Hypertrophy',
        '• Saturday: Lower Body Power',
        '• Sunday: Rest',
      ]
    },
    followup: '*Deload every 4–6 weeks. Track progressive overload weekly.*'
  },
  muscle: {
    triggers: ['muscle', 'chest', 'back', 'legs', 'arms', 'shoulders', 'core', 'glutes', 'abs'],
    response: `Here are exercise recommendations for specific muscle groups:`,
    card: {
      title: '🎯 Muscle Group Targeting',
      items: [
        '• Chest: Bench press, Dips, Cable flyes',
        '• Back: Deadlifts, Pull-ups, Barbell rows',
        '• Legs: Squats, Lunges, Leg press',
        '• Shoulders: OHP, Lateral raises, Face pulls',
        '• Core: Planks, Russian twists, Hollow holds',
        '• Arms: Curls, Tricep pushdowns, Hammer curls',
      ]
    },
    followup: '*Target each muscle group 2x per week for optimal hypertrophy.*'
  },
  nutrition: {
    triggers: ['nutrition', 'diet', 'eat', 'food', 'protein', 'calories', 'macros'],
    response: `Nutrition is crucial for fitness results! Here are the fundamentals:`,
    card: {
      title: '🥗 Nutrition Guidelines',
      items: [
        '• Protein: 1.6–2.2g per kg bodyweight/day',
        '• Carbs: 3–5g per kg on training days',
        '• Fats: 0.8–1.2g per kg bodyweight/day',
        '• Hydration: 35ml per kg bodyweight/day',
        '• Meal timing: eat within 2hr post-workout',
        '• Track calories for 2 weeks to establish baseline',
      ]
    },
    followup: '*Always consult a registered dietitian for personalised advice.*'
  },
  weight_loss: {
    triggers: ['weight loss', 'lose weight', 'fat loss', 'slim', 'cut', 'deficit', 'cardio'],
    response: `For effective fat loss while preserving muscle:`,
    card: {
      title: '📉 Fat Loss Protocol',
      items: [
        '• Caloric deficit: 300–500 kcal/day',
        '• Maintain high protein intake (2g/kg)',
        '• Strength train 3–4× per week',
        '• Cardio: 150+ min moderate intensity/week',
        '• HIIT: 2× per week, 20–30 min sessions',
        '• Sleep: 7–9 hours (crucial for fat loss)',
      ]
    },
    followup: '*Aim for 0.5–1% bodyweight loss per week for sustainable results.*'
  },
  status: {
    triggers: ['status', 'workflow', 'submit', 'approve', 'publish', 'draft'],
    response: `Here's how the **FitFlow Workout Workflow** works:`,
    card: {
      title: '🔄 Plan Workflow States',
      items: [
        '📝 DRAFT → Plan is being created/edited',
        '📤 SUBMITTED → Sent to admin for review',
        '✅ APPROVED → Admin approved the plan',
        '🚀 PUBLISHED → Live and accessible',
        '',
        'Trainers can: Create → Submit',
        'Admins can: Approve, Reject, Publish',
      ]
    },
    followup: '*Use the "Try the Demo" section to see the workflow in action!*'
  },
  default: {
    responses: [
      "That's a great question! As a demo AI, I have pre-programmed responses about workout planning, exercise selection, nutrition, and the FitFlow system. Try asking about:\n\n• Creating workout plans\n• Beginner/intermediate/advanced routines\n• Specific muscle groups\n• Fat loss or muscle building",
      "I can help with fitness topics! Try asking me about:\n\n**Workouts** – 'Create a chest workout'\n**Goals** – 'I want to lose weight'\n**Muscles** – 'Best exercises for back'\n**Nutrition** – 'How much protein do I need?'\n**System** – 'How does the workflow work?'",
    ]
  }
};

let defaultResponseIndex = 0;
let conversationCount = 0;

function getAIResponse(userMessage) {
  const msg = userMessage.toLowerCase().trim();

  // Check each category
  for (const [key, data] of Object.entries(AI_RESPONSES)) {
    if (key === 'greeting' || key === 'default') continue;
    if (data.triggers && data.triggers.some(t => msg.includes(t))) {
      return { type: 'structured', ...data };
    }
  }

  // Default rotating response
  const resp = AI_RESPONSES.default.responses[defaultResponseIndex % AI_RESPONSES.default.responses.length];
  defaultResponseIndex++;
  return { type: 'text', response: resp };
}

function formatAIMessage(response) {
  let html = '';

  if (response.type === 'structured') {
    html = `<div>${mdToHtml(response.response)}</div>`;
    if (response.card) {
      html += `<div class="ai-plan-card">
        <div class="ai-plan-card-title">${response.card.title}</div>
        ${response.card.items.map(item => item ? `<div class="ai-plan-card-item">${item}</div>` : '<br>').join('')}
      </div>`;
    }
    if (response.followup) {
      html += `<div style="margin-top:10px; font-size:12px; color:var(--text-muted);">${mdToHtml(response.followup)}</div>`;
    }
  } else {
    html = mdToHtml(response.response || response.text || '');
  }

  return html;
}

// Minimal Markdown → HTML
function mdToHtml(text) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code style="background:rgba(255,255,255,0.1);padding:1px 5px;border-radius:3px;">$1</code>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>')
    .replace(/$/, '</p>');
}

function getCurrentTime() {
  return new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true });
}

// AI chat (app-level floating assistant)
class AIAssistant {
  constructor(containerId) {
    this.container = document.getElementById(containerId);
    if (!this.container) return;
    this.messagesEl = this.container.querySelector('.ai-messages');
    this.inputEl = this.container.querySelector('.ai-input');
    this.sendBtn = this.container.querySelector('.ai-send');
    this.clearBtn = this.container.querySelector('.ai-clear');
    this.chips = this.container.querySelectorAll('.ai-chip');
    this.init();
  }

  init() {
    if (!this.container) return;
    this.sendBtn?.addEventListener('click', () => this.sendMessage());
    this.inputEl?.addEventListener('keydown', e => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.sendMessage(); }
    });
    this.clearBtn?.addEventListener('click', () => this.clearChat());
    this.chips?.forEach(chip => {
      chip.addEventListener('click', () => {
        if (this.inputEl) {
          this.inputEl.value = chip.textContent.trim();
          this.sendMessage();
        }
      });
    });
  }

  sendMessage() {
    const text = this.inputEl?.value?.trim();
    if (!text || !this.inputEl) return;
    this.inputEl.value = '';
    this.addMessage(text, 'user');
    this.sendBtn.disabled = true;
    this.showTyping();
    const delay = 800 + Math.random() * 600;
    setTimeout(() => {
      this.hideTyping();
      const response = getAIResponse(text);
      this.addAIMessage(response);
      this.sendBtn.disabled = false;
    }, delay);
  }

  addMessage(text, role) {
    const isUser = role === 'user';
    const div = document.createElement('div');
    div.className = `ai-msg ${isUser ? 'user-msg' : 'ai-msg'}`;
    div.innerHTML = `
      <div class="ai-msg-avatar ${isUser ? 'user' : 'ai'}">${isUser ? '👤' : '🤖'}</div>
      <div>
        <div class="ai-msg-bubble">${isUser ? text : text}</div>
        <div class="ai-msg-time">${getCurrentTime()}</div>
      </div>
    `;
    this.messagesEl?.appendChild(div);
    this.scrollBottom();
  }

  addAIMessage(response) {
    const div = document.createElement('div');
    div.className = 'ai-msg ai-msg';
    div.innerHTML = `
      <div class="ai-msg-avatar ai">🤖</div>
      <div>
        <div class="ai-msg-bubble">${formatAIMessage(response)}</div>
        <div class="ai-msg-time">${getCurrentTime()}</div>
      </div>
    `;
    this.messagesEl?.appendChild(div);
    this.scrollBottom();
  }

  showTyping() {
    const div = document.createElement('div');
    div.className = 'ai-msg ai-msg'; div.id = 'aiTyping';
    div.innerHTML = `
      <div class="ai-msg-avatar ai">🤖</div>
      <div class="ai-msg-bubble" style="padding:10px 16px">
        <div class="ai-typing">
          <div class="typing-dot"></div>
          <div class="typing-dot"></div>
          <div class="typing-dot"></div>
        </div>
      </div>
    `;
    this.messagesEl?.appendChild(div);
    this.scrollBottom();
  }

  hideTyping() {
    document.getElementById('aiTyping')?.remove();
  }

  clearChat() {
    if (this.messagesEl) {
      this.messagesEl.innerHTML = '';
      this.addAIMessage({ type: 'text', response: AI_RESPONSES.greeting[0] });
    }
  }

  scrollBottom() {
    if (this.messagesEl) this.messagesEl.scrollTop = this.messagesEl.scrollHeight;
  }
}

/* ================================================================
   FLOATING AI ASSISTANT (app pages)
   ================================================================ */

document.addEventListener('DOMContentLoaded', () => {
  const fab = document.getElementById('aiFab');
  const drawer = document.getElementById('aiDrawer');
  if (!fab || !drawer) return;

  let open = false;
  let initialized = false;

  fab.addEventListener('click', () => {
    open = !open;
    fab.classList.toggle('open', open);
    drawer.classList.toggle('closed', !open);

    if (open && !initialized) {
      initialized = true;
      const assistant = new AIAssistant('aiDrawer');
      assistant.addAIMessage({ type: 'text', response: AI_RESPONSES.greeting[0] });
    }
  });

  // Start closed
  drawer.classList.add('closed');
});

/* ================================================================
   INTERACTIVE DEMO PAGE
   ================================================================ */

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('demoPage')) return;
  initDemoPage();
});

function initDemoPage() {
  // Tab navigation
  const tabs = document.querySelectorAll('.demo-tab');
  const sections = document.querySelectorAll('.demo-section');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const target = tab.dataset.target;
      tabs.forEach(t => t.classList.remove('active'));
      sections.forEach(s => s.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById(target)?.classList.add('active');
    });
  });

  initDemoDashboard();
  initDemoCreate();
  initDemoSearch();
  initDemoWorkflow();
  initDemoAI();
  initDemoArchitecture();
  initDemoReset();
}

/* --- Demo State --- */
const DEMO_PLANS = [
  {
    id: 1, name: 'Morning HIIT Blast', status: 'SUBMITTED', difficulty: 'INTERMEDIATE',
    duration: 30, muscle: 'Full Body', trainer: 'Sarah M.',
    description: 'High-intensity interval training to kickstart your day.',
    exercises: 'Burpees 4×15, Mountain Climbers 4×30s, Jump Squats 4×12'
  },
  {
    id: 2, name: 'Powerlifting Fundamentals', status: 'APPROVED', difficulty: 'ADVANCED',
    duration: 75, muscle: 'Compound', trainer: 'Marcus T.',
    description: 'Focus on the Big 3: Squat, Bench, Deadlift.',
    exercises: 'Squat 5×5, Bench Press 5×5, Deadlift 3×3'
  },
  {
    id: 3, name: 'Beginner Body Weight', status: 'PUBLISHED', difficulty: 'BEGINNER',
    duration: 20, muscle: 'Full Body', trainer: 'Emma L.',
    description: 'No equipment needed. Perfect for fitness newcomers.',
    exercises: 'Push-ups 3×10, Squats 3×15, Plank 3×30s'
  },
  {
    id: 4, name: 'Core Strength Builder', status: 'DRAFT', difficulty: 'INTERMEDIATE',
    duration: 35, muscle: 'Core', trainer: 'David R.',
    description: 'Build a rock-solid core with progressive exercises.',
    exercises: 'Planks 4×45s, Dead Bugs 3×12, Russian Twists 4×20'
  },
  {
    id: 5, name: 'Upper Body Hypertrophy', status: 'SUBMITTED', difficulty: 'ADVANCED',
    duration: 60, muscle: 'Upper Body', trainer: 'Rachel K.',
    description: 'Science-based muscle building for the upper body.',
    exercises: 'Bench 4×10, OHP 4×10, Pull-ups 4×8, Rows 4×12'
  },
  {
    id: 6, name: 'Flexibility & Mobility', status: 'APPROVED', difficulty: 'BEGINNER',
    duration: 25, muscle: 'Full Body', trainer: 'Jin Y.',
    description: 'Improve range of motion and prevent injury.',
    exercises: 'Hip circles, Shoulder rolls, Pigeon pose, Cat-cow'
  },
];

let demoPlanState = JSON.parse(JSON.stringify(DEMO_PLANS)); // mutable copy

/* --- Step 1: Dashboard --- */
function initDemoDashboard() {
  renderDashboardStats();
  renderDemoRecentPlans();
}

function renderDashboardStats() {
  const stats = {
    total: demoPlanState.length,
    draft: demoPlanState.filter(p => p.status === 'DRAFT').length,
    submitted: demoPlanState.filter(p => p.status === 'SUBMITTED').length,
    approved: demoPlanState.filter(p => p.status === 'APPROVED').length,
    published: demoPlanState.filter(p => p.status === 'PUBLISHED').length,
  };

  Object.entries(stats).forEach(([key, val]) => {
    const el = document.getElementById(`demo-stat-${key}`);
    if (el) {
      el.textContent = '0';
      setTimeout(() => animateCounter(el, val, 700), 300);
    }
  });
}

function renderDemoRecentPlans() {
  const container = document.getElementById('demoRecentPlans');
  if (!container) return;
  const recent = demoPlanState.slice(0, 4);
  container.innerHTML = recent.map(p => `
    <div class="demo-search-row" style="cursor:default">
      <div class="demo-search-name">${p.name}</div>
      <div class="demo-search-tags">
        <span class="badge badge-${p.difficulty.toLowerCase()}">${p.difficulty}</span>
        <span class="status-badge status-${p.status.toLowerCase()}">${p.status}</span>
        <span class="meta-text">${p.duration} min</span>
      </div>
    </div>
  `).join('');
}

/* --- Step 2: Create --- */
function initDemoCreate() {
  const form = document.getElementById('demoCreateForm');
  if (!form) return;

  const fields = ['demoName', 'demoTrainer', 'demoDesc', 'demoDuration', 'demoDifficulty', 'demoMuscle'];
  fields.forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('input', updatePreview);
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (!validateDemoForm()) return;

    const name = document.getElementById('demoName')?.value.trim();
    const trainer = document.getElementById('demoTrainer')?.value.trim();
    const desc = document.getElementById('demoDesc')?.value.trim();
    const duration = parseInt(document.getElementById('demoDuration')?.value || '0');
    const difficulty = document.getElementById('demoDifficulty')?.value;
    const muscle = document.getElementById('demoMuscle')?.value.trim();

    const newPlan = {
      id: demoPlanState.length + 10,
      name, trainer: trainer, description: desc,
      duration, difficulty, muscle,
      status: 'DRAFT',
      exercises: 'Enter exercise details here',
    };

    demoPlanState.push(newPlan);
    renderDashboardStats();

    // Show success
    const successEl = document.getElementById('demoCreateSuccess');
    if (successEl) {
      successEl.innerHTML = `
        <div class="flash flash-success">
          <span class="flash-icon">✅</span>
          <span>Demo plan "<strong>${name}</strong>" created! It appears in your demo dashboard.</span>
        </div>`;
      setTimeout(() => { if(successEl) successEl.innerHTML = ''; }, 5000);
    }

    // Mark tab as completed
    document.querySelector('[data-target="demoCreate"]')?.classList.add('completed');

    // Reset form
    form.reset();
    updatePreview();

    showToast(`✅ Plan "${name}" added to demo!`, 'success');
  });

  updatePreview();
}

function validateDemoForm() {
  const nameEl = document.getElementById('demoName');
  const name = nameEl?.value.trim();
  let valid = true;

  if (!name || name.length < 3) {
    nameEl?.classList.add('is-invalid');
    valid = false;
  } else {
    nameEl?.classList.remove('is-invalid');
  }

  const durationEl = document.getElementById('demoDuration');
  const dur = parseInt(durationEl?.value);
  if (!dur || dur < 1 || dur > 600) {
    durationEl?.classList.add('is-invalid');
    valid = false;
  } else {
    durationEl?.classList.remove('is-invalid');
  }

  if (!valid) showToast('Please fix form errors before submitting.', 'error');
  return valid;
}

function updatePreview() {
  const get = id => document.getElementById(id)?.value || '';
  const setPreview = (id, value) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = value || '—';
    el.className = `preview-value ${value ? '' : 'empty'}`;
  };

  setPreview('previewName', get('demoName'));
  setPreview('previewDifficulty', get('demoDifficulty'));
  setPreview('previewDuration', get('demoDuration') ? get('demoDuration') + ' min' : '');
  setPreview('previewMuscle', get('demoMuscle'));
  setPreview('previewStatus', 'DRAFT ← new plans start here');
}

/* --- Step 3: Search & Filter --- */
function initDemoSearch() {
  const searchInput = document.getElementById('demoSearchInput');
  const statusFilter = document.getElementById('demoStatusFilter');
  const diffFilter = document.getElementById('demoDiffFilter');
  const clearBtn = document.getElementById('demoSearchClear');

  if (!searchInput) return;

  const doFilter = () => {
    const kw = searchInput.value.toLowerCase().trim();
    const st = statusFilter?.value || '';
    const diff = diffFilter?.value || '';

    const results = demoPlanState.filter(p => {
      const matchKw = !kw || p.name.toLowerCase().includes(kw) || p.muscle.toLowerCase().includes(kw);
      const matchSt = !st || p.status === st;
      const matchDiff = !diff || p.difficulty === diff;
      return matchKw && matchSt && matchDiff;
    });

    renderSearchResults(results, kw);
    document.querySelector('[data-target="demoSearch"]')?.classList.add('completed');
  };

  searchInput.addEventListener('input', doFilter);
  statusFilter?.addEventListener('change', doFilter);
  diffFilter?.addEventListener('change', doFilter);
  clearBtn?.addEventListener('click', () => {
    if (searchInput) searchInput.value = '';
    if (statusFilter) statusFilter.value = '';
    if (diffFilter) diffFilter.value = '';
    doFilter();
  });

  doFilter();
}

function renderSearchResults(plans, keyword) {
  const container = document.getElementById('demoSearchResults');
  const count = document.getElementById('demoResultCount');
  if (!container) return;

  if (count) count.textContent = `${plans.length} plan${plans.length !== 1 ? 's' : ''} found`;

  if (plans.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding:40px 20px">
        <div class="empty-icon">🔍</div>
        <h3>No results found</h3>
        <p>Try different keywords or clear the filters</p>
      </div>`;
    return;
  }

  container.innerHTML = plans.map(p => {
    let name = p.name;
    if (keyword) {
      const re = new RegExp(`(${keyword})`, 'gi');
      name = name.replace(re, '<mark style="background:rgba(124,111,255,0.3);color:var(--primary);border-radius:2px;padding:0 2px">$1</mark>');
    }
    return `
      <div class="demo-search-row">
        <div class="demo-search-name">${name}</div>
        <div class="demo-search-tags">
          <span class="meta-text">🎯 ${p.muscle}</span>
          <span class="badge badge-${p.difficulty.toLowerCase()}">${p.difficulty}</span>
          <span class="status-badge status-${p.status.toLowerCase()}">${p.status}</span>
          <span class="meta-text">⏱ ${p.duration}min</span>
        </div>
      </div>`;
  }).join('');
}

/* --- Step 4: Workflow --- */
function initDemoWorkflow() {
  const selector = document.getElementById('demoWfSelector');
  if (!selector) return;

  renderWfSelector();
  selectWfPlan(demoPlanState[0].id);
}

function renderWfSelector() {
  const container = document.getElementById('demoWfSelector');
  if (!container) return;
  container.innerHTML = demoPlanState.slice(0, 4).map(p => `
    <div class="demo-wf-plan-item" onclick="selectWfPlan(${p.id})" id="wf-item-${p.id}">
      <div class="demo-wf-plan-name">${p.name}</div>
      <span class="status-badge status-${p.status.toLowerCase()}">${p.status}</span>
    </div>
  `).join('');
}

function selectWfPlan(id) {
  const plan = demoPlanState.find(p => p.id === id);
  if (!plan) return;

  document.querySelectorAll('.demo-wf-plan-item').forEach(el => el.classList.remove('selected'));
  document.getElementById(`wf-item-${id}`)?.classList.add('selected');

  renderWfDetail(plan);
}
window.selectWfPlan = selectWfPlan;

function renderWfDetail(plan) {
  const container = document.getElementById('demoWfDetail');
  if (!container) return;

  const states = ['DRAFT', 'SUBMITTED', 'APPROVED', 'PUBLISHED'];
  const currentIdx = states.indexOf(plan.status);

  const transitions = {
    DRAFT: [{ label: '📤 Submit for Review', next: 'SUBMITTED', cls: 'btn-warning' }],
    SUBMITTED: [
      { label: '✅ Approve', next: 'APPROVED', cls: 'btn-success' },
      { label: '↩️ Reject to Draft', next: 'DRAFT', cls: 'btn-danger' },
    ],
    APPROVED: [{ label: '🚀 Publish', next: 'PUBLISHED', cls: 'btn-primary' }],
    PUBLISHED: [],
  };

  const wfDots = states.map((s, i) => {
    let cls = '';
    if (i < currentIdx) cls = 'wf-done';
    if (i === currentIdx) cls = 'wf-current';
    return `
      <div class="wf-step ${cls}"><div class="wf-dot"></div><div class="wf-label">${s}</div></div>
      ${i < states.length - 1 ? `<div class="wf-line ${i < currentIdx ? 'done' : ''}"></div>` : ''}
    `;
  }).join('');

  const buttons = transitions[plan.status]?.map(t => `
    <button class="btn ${t.cls}" onclick="applyWfTransition(${plan.id}, '${t.next}')">${t.label}</button>
  `).join('') || '<p style="color:var(--text-muted);font-size:13px">🔒 This plan is published — no further transitions.</p>';

  container.innerHTML = `
    <div class="demo-workflow-card">
      <h3 style="font-size:17px;font-weight:700;margin-bottom:18px;font-family:'Outfit',sans-serif">${plan.name}</h3>
      <div class="workflow-steps" style="margin-bottom:24px">${wfDots}</div>
      <div class="status-actions">${buttons}</div>
      <div id="wfMessage" style="margin-top:14px"></div>
    </div>
  `;
}
window.applyWfTransition = applyWfTransition;

function applyWfTransition(id, newStatus) {
  const plan = demoPlanState.find(p => p.id === id);
  if (!plan) return;

  const validTransitions = {
    DRAFT: ['SUBMITTED'],
    SUBMITTED: ['APPROVED', 'DRAFT'],
    APPROVED: ['PUBLISHED'],
    PUBLISHED: [],
  };

  if (!validTransitions[plan.status]?.includes(newStatus)) {
    showToast(`Invalid transition from ${plan.status} to ${newStatus}`, 'error');
    return;
  }

  plan.status = newStatus;

  // Update selector item
  const item = document.getElementById(`wf-item-${id}`);
  if (item) {
    item.querySelector('.status-badge').textContent = newStatus;
    item.querySelector('.status-badge').className = `status-badge status-${newStatus.toLowerCase()}`;
  }

  renderWfDetail(plan);
  renderDashboardStats();
  document.querySelector('[data-target="demoWorkflow"]')?.classList.add('completed');

  const msgEl = document.getElementById('wfMessage');
  if (msgEl) {
    msgEl.innerHTML = `<div class="flash flash-success"><span>✅ Status changed to <strong>${newStatus}</strong> — this is isolated demo data.</span></div>`;
    setTimeout(() => { if(msgEl) msgEl.innerHTML=''; }, 4000);
  }

  showToast(`Status → ${newStatus} (Demo)`, 'success');
}

/* --- Step 5: AI Demo --- */
function initDemoAI() {
  const demoAI = new AIAssistant('demoAiPanel');
  if (demoAI.messagesEl) {
    demoAI.addAIMessage({ type: 'text', response: AI_RESPONSES.greeting[0] });
  }
  document.querySelector('[data-target="demoAI"]')?.addEventListener('click', () => {
    document.querySelector('[data-target="demoAI"]')?.classList.add('completed');
  });
}

/* --- Step 6: DevOps Architecture --- */
const ARCH_INFO = {
  'arch-git': {
    title: '📁 Source Control – GitHub',
    text: 'The repository lives on GitHub with branch protection and pull-request workflows. The CI/CD pipeline is triggered automatically on every push to main via webhooks.',
    techs: ['Git', 'GitHub', 'Branch protection', 'PR reviews'],
    exists: true,
  },
  'arch-jenkins': {
    title: '⚙️ CI/CD – Jenkins Pipeline',
    text: 'A declarative Jenkinsfile orchestrates the full pipeline: Maven build → unit tests → Docker image build → push to registry → integration tests → deployment. The pipeline runs inside Docker agents.',
    techs: ['Jenkinsfile', 'Maven Surefire', 'Maven Failsafe', 'Docker agents'],
    exists: true,
  },
  'arch-docker': {
    title: '🐳 Containerisation – Docker + Compose',
    text: 'The application and MySQL database each run in separate containers. docker-compose.yml defines the production stack; docker-compose.ci.yml is used in the Jenkins pipeline. Health checks ensure the DB is ready before the app starts.',
    techs: ['Dockerfile', 'Docker Compose', 'MySQL 8', 'Health checks'],
    exists: true,
  },
  'arch-app': {
    title: '☕ Application – Spring Boot 3.2',
    text: 'The backend is a Spring Boot 3.2 MVC application using Thymeleaf for server-side rendering, Spring Security for role-based access control (ADMIN/TRAINER), Spring Data JPA for persistence, and Bean Validation for input validation.',
    techs: ['Spring Boot 3.2', 'Thymeleaf', 'Spring Security', 'JPA/Hibernate', 'MySQL'],
    exists: true,
  },
  'arch-db': {
    title: '🗄️ Database – MySQL 8',
    text: 'MySQL 8 stores all workout plans with full audit fields (createdAt, updatedAt, createdBy). Schema is auto-managed by Hibernate DDL. H2 in-memory database is used for unit tests to keep them isolated and fast.',
    techs: ['MySQL 8', 'H2 (tests)', 'Hibernate DDL', 'Audit columns'],
    exists: true,
  },
  'arch-tests': {
    title: '🧪 Testing – JUnit + Selenium',
    text: 'Unit tests use JUnit 5 + Mockito with H2 in-memory database. Integration/E2E tests use Selenium WebDriver + TestNG driven by Maven Failsafe plugin. Spring Security Test verifies RBAC.',
    techs: ['JUnit 5', 'Mockito', 'Selenium 4', 'TestNG', 'Spring Security Test'],
    exists: true,
  },
  'arch-ansible': {
    title: '📜 Configuration Management – Ansible',
    text: 'Ansible playbooks provision target servers: install Docker, configure firewall rules, set environment variables from vault, and deploy the Docker Compose stack. Includes rollback playbooks.',
    techs: ['Ansible', 'Ansible Vault', 'Docker module', 'Idempotent playbooks'],
    exists: true,
  },
};

function initDemoArchitecture() {
  document.querySelectorAll('.arch-node').forEach(node => {
    node.addEventListener('click', () => {
      const key = node.id;
      const info = ARCH_INFO[key];
      if (!info) return;

      document.querySelectorAll('.arch-node').forEach(n => n.classList.remove('active'));
      node.classList.add('active');

      const infoEl = document.getElementById('archInfo');
      if (infoEl) {
        infoEl.classList.add('active');
        infoEl.innerHTML = `
          <div class="arch-info-title">${info.title}</div>
          <div class="arch-info-text">${info.text}</div>
          <div class="arch-tech-tags">${info.techs.map(t => `<span class="arch-tech-tag">${t}</span>`).join('')}</div>
        `;
        document.querySelector('[data-target="demoArch"]')?.classList.add('completed');
      }
    });
  });
}

/* --- Demo Reset --- */
function initDemoReset() {
  const btn = document.getElementById('demoResetBtn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    const confirmed = await createConfirmModal(
      'All demo plans you created will be removed and states reset to defaults.',
      'Reset Demo Data?',
      '🔄'
    );
    if (!confirmed) return;
    demoPlanState = JSON.parse(JSON.stringify(DEMO_PLANS));
    renderDashboardStats();
    renderDemoRecentPlans();
    renderWfSelector();
    selectWfPlan(demoPlanState[0].id);
    document.querySelectorAll('.demo-tab').forEach(t => t.classList.remove('completed'));

    // Reset search
    const si = document.getElementById('demoSearchInput');
    if (si) si.value = '';
    const sf = document.getElementById('demoStatusFilter');
    if (sf) sf.value = '';
    const df = document.getElementById('demoDiffFilter');
    if (df) df.value = '';

    showToast('Demo data has been reset to defaults.', 'info');
  });
}
