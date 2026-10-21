package app.constancia.data.local

import app.constancia.db.Challenge as ChallengeRow
import app.constancia.db.Checkin as CheckinRow
import app.constancia.db.Diary_entry as DiaryEntryRow
import app.constancia.db.Habit as HabitRow
import app.constancia.db.Pause_interval as PauseIntervalRow
import app.constancia.db.Saved_view as SavedViewRow
import app.constancia.db.Subtask as SubtaskRow
import app.constancia.db.Task_goal_pause as GoalPauseRow
import app.constancia.db.Task_item as TaskItemRow
import app.constancia.db.Task_template as TaskTemplateRow
import app.constancia.db.Task_template_item as TaskTemplateItemRow
import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.FrequencyType
import app.constancia.model.GoalPause
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.PauseInterval
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate
import app.constancia.model.TaskTemplateItem

// Conversoes entidade SQLDelight -> modelo de dominio.
// Colunas INTEGER do SQLite chegam como Long; flags 0/1 viram Boolean.

internal fun HabitRow.toModel(): Habit = Habit(
    id = id,
    name = name,
    icon = icon,
    color = color,
    goalType = goal_type.toGoalType(),
    targetQuantity = target_quantity,
    quantityUnit = quantity_unit,
    frequencyType = frequency_type.toFrequencyType(),
    daysOfWeek = days_of_week.toDayList(),
    weeklyTarget = weekly_target.toInt(),
    session = session.toSession(),
    reminderEnabled = reminder_enabled.isSet(),
    reminderTime = reminder_time,
    paused = paused.isSet(),
    createdAt = created_at,
    sortOrder = sort_order.toInt(),
)

internal fun CheckinRow.toModel(): Checkin = Checkin(
    id = id,
    habitId = habit_id,
    date = date,
    quantity = quantity,
)

internal fun PauseIntervalRow.toModel(): PauseInterval = PauseInterval(
    id = id,
    habitId = habit_id,
    startDate = start_date,
    endDate = end_date,
)

internal fun TaskItemRow.toModel(subtasks: List<Subtask> = emptyList()): TaskItem = TaskItem(
    id = id,
    title = title,
    dueTime = due_time,
    done = done.isSet(),
    date = date,
    project = project.toTaskProject(),
    priority = priority.toInt(),
    dueDate = due_date,
    session = session.toSession(),
    repeatRule = repeat_rule,
    note = note,
    habitId = habit_id,
    subtasks = subtasks,
    deadlineDate = deadline_date,
    durationMinutes = duration_minutes?.toInt(),
    completedAt = completed_at,
    reminderMinutesBefore = reminder_minutes_before?.toInt(),
    repeatSpec = repeat_spec,
)

internal fun SavedViewRow.toModel(): SavedView = SavedView(
    id = id,
    name = name,
    colorHex = color,
    project = project?.toTaskProjectOrNull(),
    priority = priority?.toInt(),
    period = period,
    groupBy = group_by,
    sortOrder = sort_order.toInt(),
)

internal fun TaskTemplateRow.toModel(items: List<TaskTemplateItem>): TaskTemplate =
    TaskTemplate(
        id = id,
        name = name,
        isFactory = is_factory.isSet(),
        items = items,
    )

internal fun TaskTemplateItemRow.toModel(): TaskTemplateItem = TaskTemplateItem(
    id = id,
    templateId = template_id,
    title = title,
    project = project.toTaskProject(),
    priority = priority.toInt(),
    session = session.toSession(),
    note = note,
    sortOrder = sort_order.toInt(),
)

internal fun GoalPauseRow.toModel(): GoalPause = GoalPause(
    id = id,
    startDate = start_date,
    endDate = end_date,
)

internal fun SubtaskRow.toModel(): Subtask = Subtask(
    id = id,
    taskId = task_id,
    title = title,
    done = done.isSet(),
    sortOrder = sort_order.toInt(),
)

internal fun DiaryEntryRow.toModel(): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    mood = mood?.toInt(),
    note = note,
    energy = energy?.toInt(),
    sleepHours = sleep_hours,
    tags = tags.toTagList(),
    photoUri = photo_uri,
    prompt = prompt,
)

internal fun ChallengeRow.toModel(): Challenge = Challenge(
    id = id,
    title = title,
    description = description,
    durationDays = duration_days.toInt(),
    habitName = habit_name,
    habitIcon = habit_icon,
    startDate = start_date,
    status = status.toChallengeStatus(),
    isPro = is_pro.isSet(),
)

// Conversoes modelo -> valores de banco (usadas nos inserts/updates).

internal fun Habit.daysOfWeekCsv(): String = daysOfWeek.joinToString(",")

internal fun Long.isSet(): Boolean = this == 1L

internal fun Boolean.toFlag(): Long = if (this) 1L else 0L

internal fun String.toGoalType(): GoalType =
    GoalType.entries.firstOrNull { it.name == this } ?: GoalType.BINARY

internal fun String.toFrequencyType(): FrequencyType =
    FrequencyType.entries.firstOrNull { it.name == this } ?: FrequencyType.DAILY

internal fun String.toSession(): Session =
    Session.entries.firstOrNull { it.name == this } ?: Session.MANHA

internal fun String.toChallengeStatus(): ChallengeStatus =
    ChallengeStatus.entries.firstOrNull { it.name == this } ?: ChallengeStatus.AVAILABLE

internal fun String.toTaskProject(): TaskProject =
    TaskProject.entries.firstOrNull { it.name.lowercase() == this } ?: TaskProject.ENTRADA

/** Como [toTaskProject], mas preserva null (filtros de vista salva). */
internal fun String.toTaskProjectOrNull(): TaskProject? =
    TaskProject.entries.firstOrNull { it.name.lowercase() == this }

internal fun TaskProject.toDbValue(): String = name.lowercase()

// tags do diário: csv "Trabalho,Treino" -> List<String>; vazio -> lista vazia.
internal fun String.toTagList(): List<String> =
    if (isBlank()) emptyList()
    else split(",").map { it.trim() }.filter { it.isNotEmpty() }

internal fun List<String>.toTagCsv(): String = joinToString(",")

// days_of_week: csv "1,3,5" (1=domingo) -> List<Int>; vazio -> lista vazia.
internal fun String.toDayList(): List<Int> =
    if (isBlank()) emptyList()
    else split(",").mapNotNull { it.trim().toIntOrNull() }
