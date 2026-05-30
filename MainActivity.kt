// Объявление пакета (папки, где лежит файл)
package com.example.simplemedicaldiary

// === ИМПОРТЫ (подключение внешних библиотек и классов Android) ===

import android.Manifest                   // Разрешения Android (например, для уведомлений)
import android.app.NotificationChannel   // Канал уведомлений (нужен для Android 8+)
import android.app.NotificationManager   // Менеджер для отправки уведомлений
import android.app.TimePickerDialog      // Диалог выбора времени
import android.content.Context           // Контекст приложения (доступ к ресурсам)
import android.content.SharedPreferences // Хранение простых данных (ключ-значение)
import android.content.pm.PackageManager // Проверка разрешений
import android.graphics.Color            // Работа с цветами
import android.os.Build                  // Информация о версии Android
import android.os.Bundle                 // Передача данных между компонентами
import android.view.View                 // Базовый класс для всех UI элементов
import android.widget.*                  // Все виджеты (Button, TextView, EditText и т.д.)
import androidx.appcompat.app.AlertDialog // Диалоговое окно
import androidx.appcompat.app.AppCompatActivity // Базовый класс для экрана (Activity)
import androidx.core.app.ActivityCompat   // Для запроса разрешений
import androidx.core.content.ContextCompat // Проверка разрешений
import androidx.work.*                   // WorkManager для фоновых задач
import com.github.mikephil.charting.charts.LineChart // График (линейный)
import com.github.mikephil.charting.components.XAxis // Ось X графика
import com.github.mikephil.charting.data.Entry       // Точка на графике (x, y)
import com.github.mikephil.charting.data.LineData    // Данные для графика
import com.github.mikephil.charting.data.LineDataSet // Набор данных для линии графика
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter // Формат подписей оси X
import java.text.SimpleDateFormat         // Форматирование даты и времени
import java.util.*                        // Collections, Calendar, Date
import java.util.concurrent.TimeUnit      // Константы времени (секунды, минуты, часы)

// === ГЛАВНЫЙ КЛАСС АКТИВНОСТИ ===
// AppCompatActivity - поддержка старых версий Android
class MainActivity : AppCompatActivity() {

    // === СВОЙСТВА (переменные класса) ===

    private lateinit var prefs: SharedPreferences   // Хранилище данных (SharedPreferences)
    private lateinit var moodChart: LineChart       // График настроения
    private lateinit var medicineList: LinearLayout // Контейнер для списка лекарств
    private lateinit var moodDisplay: TextView      // Текст с историей настроений
    private lateinit var symptomsInput: EditText    // Поле ввода симптомов

    // Список всех записей настроения (хранится в памяти)
    private val moodEntries = mutableListOf<MoodEntry>()

    // Список возможных триггеров (что вызвало эмоцию)
    private val triggerList = listOf(
        "Без триггера", "💼 Работа/учеба", "👨‍👩‍👧 Семья/отношения",
        "💰 Финансы", "🩺 Здоровье", "🌙 Бессонница/усталость",
        "📱 Соцсети/новости", "🚗 Дорога/транспорт", "🎓 Экзамен/дедлайн",
        "🤝 Конфликт", "🌧️ Погода", "🍔 Питание", "🏋️ Спорт", "🎉 Праздник"
    )

    // Список возможных копинг-стратегий (что помогло справиться)
    private val copingList = listOf(
        "Без стратегии", "🧘 Дыхание", "💬 Поговорил с другом", "🎵 Музыка",
        "📝 Записал мысли", "🚶 Прогулка", "🏃 Физическая активность",
        "🎮 Хобби", "😴 Сон/отдых", "🍵 Чай/кофе", "🙏 Медитация", "📺 Фильм"
    )

    // === onCreate - ПЕРВЫЙ МЕТОД, ВЫЗЫВАЕМЫЙ ПРИ ЗАПУСКЕ ПРИЛОЖЕНИЯ ===
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)          // Вызов родительского конструктора
        setContentView(R.layout.activity_main)       // Устанавливаем layout (экран) из XML

        // Инициализация SharedPreferences (файл "diary" для хранения данных)
        prefs = getSharedPreferences("diary", MODE_PRIVATE)

        // Находим View (элементы интерфейса) по их ID из XML
        moodChart = findViewById(R.id.moodChart)          // График
        medicineList = findViewById(R.id.medicineList)    // Список лекарств
        moodDisplay = findViewById(R.id.moodDisplay)      // История настроений
        symptomsInput = findViewById(R.id.symptomsInput)  // Поле для симптомов

        // Запрашиваем разрешение на показ уведомлений (для Android 13+)
        requestNotificationPermission()

        // Инициализируем три основные секции приложения
        setupMedicineSection()  // Лекарства
        setupMoodSection()      // Настроение + график + триггеры + копинг
        setupBMISection()       // Калькулятор ИМТ

        // Загружаем сохранённые записи настроений из SharedPreferences
        loadMoods()
    }

    // === ЗАПРОС РАЗРЕШЕНИЯ НА УВЕДОМЛЕНИЯ ===
    private fun requestNotificationPermission() {
        // Если версия Android 13 (Tiramisu) или выше
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Проверяем, дано ли разрешение на уведомления
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                // Если не дано - запрашиваем
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    // ==================== ЛЕКАРСТВА ====================

    // Настройка секции лекарств
    private fun setupMedicineSection() {
        // При нажатии на кнопку "Добавить лекарство" показываем диалог
        findViewById<Button>(R.id.addMedicineBtn).setOnClickListener { showAddMedicineDialog() }
        // Обновляем список лекарств (показываем сохранённые)
        refreshMedicineList()
    }

    // Диалог добавления нового лекарства
    private fun showAddMedicineDialog() {
        // Поле ввода названия лекарства
        val nameInput = EditText(this).apply { hint = "Название" }
        // Поле ввода дозировки
        val dosageInput = EditText(this).apply { hint = "Дозировка" }

        // Контейнер для полей ввода (вертикальный)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(nameInput)
            addView(dosageInput)
        }

        // Переменная для хранения выбранного времени (по умолчанию 09:00)
        var selectedTime = "09:00"

        // Кнопка выбора времени
        val timeButton = Button(this).apply {
            text = "⏰ Время: $selectedTime"
            setOnClickListener {
                // Показываем диалог выбора времени
                TimePickerDialog(this@MainActivity, { _, hour, minute ->
                    selectedTime = String.format("%02d:%02d", hour, minute)
                    text = "⏰ Время: $selectedTime"
                }, 9, 0, true).show()
            }
        }
        layout.addView(timeButton)

        // Создаём диалоговое окно
        AlertDialog.Builder(this)
            .setTitle(" Новое лекарство")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = nameInput.text.toString().trim()
                val dosage = dosageInput.text.toString().trim()
                // Проверяем, что поля не пустые
                if (name.isNotBlank() && dosage.isNotBlank()) {
                    val medicines = getMedicines().toMutableList()
                    medicines.add(Medicine(name, dosage, selectedTime, false))
                    saveMedicinesList(medicines)
                    refreshMedicineList()
                    scheduleNotification(name, dosage, selectedTime)  // Планируем уведомление
                    Toast.makeText(this, "$name добавлено!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Заполните поля", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    // Обновление списка лекарств на экране
    private fun refreshMedicineList() {
        medicineList.removeAllViews()          // Очищаем контейнер
        val medicines = getMedicines()         // Получаем список из хранилища

        // Если список пуст - показываем сообщение
        if (medicines.isEmpty()) {
            medicineList.addView(TextView(this).apply {
                text = "📭 Список пуст"
                gravity = android.view.Gravity.CENTER
                setPadding(16, 32, 16, 32)
            })
            return
        }
        // Для каждого лекарства создаём карточку и добавляем в список
        medicines.forEach { medicineList.addView(createMedicineCard(it)) }
    }

    // Создание карточки (визуального элемента) для одного лекарства
    private fun createMedicineCard(medicine: Medicine): LinearLayout {
        // Горизонтальный контейнер для карточки
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.setPadding(16, 12, 16, 12)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 }
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)

        // Текст с информацией о лекарстве
        val textView = TextView(this)
        textView.text = if (medicine.isTaken) " ${medicine.name}\n${medicine.dosage} • ${medicine.time}"
        else "💊 ${medicine.name}\n${medicine.dosage} • ${medicine.time}"
        textView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        card.addView(textView)

        // Кнопка "Принять" (если уже принято - кнопка неактивна)
        val statusButton = Button(this)
        statusButton.text = if (medicine.isTaken) "✔️" else "Принять"
        if (!medicine.isTaken) {
            statusButton.setOnClickListener {
                val medicines = getMedicines().toMutableList()
                val index = medicines.indexOfFirst { it.name == medicine.name && it.time == medicine.time }
                if (index != -1) {
                    medicines[index] = medicines[index].copy(isTaken = true)
                    saveMedicinesList(medicines)
                    refreshMedicineList()
                    Toast.makeText(this@MainActivity, "Принято!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            statusButton.isEnabled = false
        }
        card.addView(statusButton)

        // Кнопка удаления лекарства (корзина)
        val deleteButton = Button(this)
        deleteButton.text = "🗑"
        deleteButton.setOnClickListener {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Удалить")
                .setMessage("Удалить ${medicine.name}?")
                .setPositiveButton("Да") { _, _ ->
                    val medicines = getMedicines().toMutableList()
                    medicines.removeAll { it.name == medicine.name && it.time == medicine.time }
                    saveMedicinesList(medicines)
                    refreshMedicineList()
                }.setNegativeButton("Нет", null).show()
        }
        card.addView(deleteButton)
        return card
    }

    // Получение списка лекарств из SharedPreferences
    private fun getMedicines(): List<Medicine> {
        val json = prefs.getString("medicines", "") ?: return emptyList()
        // Разбираем строку: "название||дозировка||время||принято|||..."
        return json.split("|||").mapNotNull {
            val parts = it.split("||")
            if (parts.size == 4) try {
                Medicine(parts[0], parts[1], parts[2], parts[3].toBoolean())
            } catch (e: Exception) { null } else null
        }
    }

    // Сохранение списка лекарств в SharedPreferences
    private fun saveMedicinesList(medicines: List<Medicine>) {
        // Превращаем список в строку с разделителем "|||"
        val json = medicines.joinToString("|||") { "${it.name}||${it.dosage}||${it.time}||${it.isTaken}" }
        prefs.edit().putString("medicines", json).apply()
    }

    // ==================== НАСТРОЕНИЕ + ТРИГГЕРЫ + КОПИНГ ====================

    // Настройка секции настроения
    private fun setupMoodSection() {
        // Кнопка "Записать" - показывает диалог выбора настроения
        findViewById<Button>(R.id.addMoodBtn).setOnClickListener { showMoodDialog() }
        // Кнопка "Очистить всё" - удаляет все записи
        findViewById<Button>(R.id.clearHistoryBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить всё")
                .setMessage("Удалить все записи?")
                .setPositiveButton("Да") { _, _ ->
                    moodEntries.clear()
                    saveMoods()
                    refreshMoodDisplay()
                    updateChart()
                    Toast.makeText(this, "Все записи удалены", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Нет", null)
                .show()
        }
        // При нажатии на текст с историей - показываем диалог удаления записи
        moodDisplay.setOnClickListener {
            if (moodEntries.isNotEmpty()) showDeleteDialog()
        }
        setupChart()
    }

    // Настройка внешнего вида графика
    private fun setupChart() {
        moodChart.apply {
            description.isEnabled = false          // Убираем описание
            setTouchEnabled(true)                  // Включаем касания
            isDragEnabled = true                   // Можно тащить
            setScaleEnabled(true)                  // Можно масштабировать

            // Настройка оси X
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM  // Ось снизу
                granularity = 1f                       // Шаг 1
                setLabelRotationAngle(45f)             // Поворачиваем подписи на 45°
                textSize = 10f
            }

            // Настройка левой оси Y (от 0.5 до 5.5, шаг 1)
            axisLeft.apply {
                axisMinimum = 0.5f
                axisMaximum = 5.5f
                granularity = 1f
                textSize = 12f
            }

            axisRight.isEnabled = false          // Правую ось отключаем
            legend.isEnabled = true              // Легенда включена
            setBackgroundColor(Color.WHITE)      // Белый фон
        }
        updateChart()
    }

    // Обновление графика на основе данных настроений
    private fun updateChart() {
        // Если нет записей - показываем сообщение
        if (moodEntries.isEmpty()) {
            moodChart.clear()
            moodChart.setNoDataText("Нет данных для графика")
            moodChart.invalidate()
            return
        }

        val entries = mutableListOf<Entry>()     // Точки графика (x, y)
        val labels = mutableListOf<String>()     // Подписи дат

        // Сортируем записи по дате и заполняем точки
        moodEntries.sortedBy { it.datetime }.forEachIndexed { index, entry ->
            entries.add(Entry(index.toFloat(), entry.mood.toFloat()))
            labels.add("${entry.datetime.substring(5, 16)}")  // Показываем "MM-dd HH:mm"
        }

        // Набор данных для графика
        val dataSet = LineDataSet(entries, "Динамика настроения").apply {
            color = Color.rgb(66, 133, 244)       // Синий цвет линии
            setCircleColor(Color.rgb(66, 133, 244))
            lineWidth = 2f
            circleRadius = 5f
            setDrawCircleHole(true)               // Рисуем дырочку в кружках
            setDrawValues(true)                   // Показывать значения на точках
            valueTextSize = 10f
            setDrawFilled(true)                   // Заливка под линией
            fillColor = Color.rgb(66, 133, 244)
            fillAlpha = 50                        // Полупрозрачная заливка
        }

        // Форматируем подписи на оси X
        moodChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        moodChart.data = LineData(dataSet)
        moodChart.invalidate()                    // Перерисовываем график
    }

    // Диалог выбора настроения, триггера и копинг-стратегии
    private fun showMoodDialog() {
        // Контейнер для элементов диалога
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Текст-подсказка
        layout.addView(TextView(this).apply {
            text = "😊 Оцените настроение (от 1 до 5):"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        })

        // RatingBar - выбор от 1 до 5 звёзд
        val ratingBar = RatingBar(this).apply {
            numStars = 5
            stepSize = 1f
            rating = 3f                             // Значение по умолчанию
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(ratingBar)

        // Разделитель (серая линия)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.LTGRAY)
            setPadding(0, 16, 0, 16)
        }
        layout.addView(divider)

        // Триггер (выпадающий список)
        layout.addView(TextView(this).apply {
            text = "⚡ Триггер (что вызвало эмоцию?):"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })
        val triggerSpinner = Spinner(this)
        triggerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, triggerList)
        layout.addView(triggerSpinner)

        // Разделитель
        val divider2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.LTGRAY)
            setPadding(0, 16, 0, 16)
        }
        layout.addView(divider2)

        // Копинг-стратегия (выпадающий список)
        layout.addView(TextView(this).apply {
            text = "🛡️ Копинг-стратегия (что помогло?):"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })
        val copingSpinner = Spinner(this)
        copingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, copingList)
        layout.addView(copingSpinner)

        // Создаём и показываем диалог
        AlertDialog.Builder(this)
            .setTitle("😊 Новая запись настроения")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val mood = ratingBar.rating.toInt()

                // Проверка: настроение должно быть от 1 до 5
                if (mood < 1 || mood > 5) {
                    Toast.makeText(this, "Выберите настроение от 1 до 5", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val symptoms = symptomsInput.text.toString().trim()
                val trigger = triggerSpinner.selectedItem.toString()
                val coping = copingSpinner.selectedItem.toString()
                val datetime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                // Добавляем новую запись в список
                moodEntries.add(MoodEntry(datetime, mood, symptoms, trigger, coping))
                saveMoods()                     // Сохраняем в SharedPreferences
                refreshMoodDisplay()            // Обновляем отображение истории
                updateChart()                   // Обновляем график
                symptomsInput.text.clear()      // Очищаем поле симптомов

                // Показываем Toast с подтверждением
                val moodEmoji = when(mood) { 1->"😢";2->"😕";3->"😐";4->"🙂";5->"😄";else->"?" }
                Toast.makeText(this, " $moodEmoji Настроение: $mood/5\n⚡ $trigger\n🛡️ $coping", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Обновление отображения истории настроений на экране
    private fun refreshMoodDisplay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayEntries = moodEntries.filter { it.datetime.startsWith(today) }.sortedBy { it.datetime }

        // Формируем текст для сегодняшних записей
        val todayText = if (todayEntries.isEmpty()) "Нет записей" else
            todayEntries.joinToString("\n") { entry ->
                val time = entry.datetime.substring(11, 16)
                val emoji = when(entry.mood) { 1->"😢";2->"😕";3->"😐";4->"🙂";5->"😄";else->"?" }
                var line = "   $time: $emoji (${entry.mood}/5)"
                if (entry.trigger != "Без триггера") line += " | ⚡${entry.trigger}"
                if (entry.coping != "Без стратегии") line += " | 🛡️${entry.coping}"
                if (entry.symptoms.isNotBlank()) line += " | 🤒${entry.symptoms}"
                line
            }

        // Формируем текст для всей истории (последние записи)
        val historyText = if (moodEntries.isEmpty()) "Нет записей" else
            moodEntries.sortedByDescending { it.datetime }.joinToString("\n") { entry ->
                val emoji = when(entry.mood) { 1->"😢";2->"😕";3->"😐";4->"🙂";5->"😄";else->"?" }
                var line = "📅 ${entry.datetime}: $emoji (${entry.mood}/5)"
                if (entry.trigger != "Без триггера") line += " | ⚡${entry.trigger}"
                if (entry.coping != "Без стратегии") line += " | 🛡️${entry.coping}"
                if (entry.symptoms.isNotBlank()) line += " | 🤒${entry.symptoms.take(20)}"
                line
            }

        // Выводим текст на экран
        moodDisplay.text = """
📅 Сегодня ($today):
$todayText

─────────────────
📜 Всего записей: ${moodEntries.size}

📋 История записей:
$historyText

💡 Нажмите на текст выше, чтобы удалить запись
""".trimIndent()
    }

    // Загрузка записей настроения из SharedPreferences
    private fun loadMoods() {
        val json = prefs.getString("moods", "") ?: return
        if (json.isEmpty()) return

        moodEntries.clear()
        // Разбираем строку: "дата||оценка||симптомы||триггер||копинг|||..."
        json.split("|||").forEach { entryStr ->
            if (entryStr.isNotEmpty()) {
                val parts = entryStr.split("||")
                if (parts.size >= 5) {
                    moodEntries.add(MoodEntry(
                        datetime = parts[0],
                        mood = parts[1].toInt(),
                        symptoms = parts[2],
                        trigger = parts[3],
                        coping = parts[4]
                    ))
                }
            }
        }
        refreshMoodDisplay()
        updateChart()
    }

    // Сохранение записей настроения в SharedPreferences
    private fun saveMoods() {
        // Превращаем список в строку с разделителем "|||"
        val json = moodEntries.joinToString("|||") {
            "${it.datetime}||${it.mood}||${it.symptoms}||${it.trigger}||${it.coping}"
        }
        prefs.edit().putString("moods", json).apply()
    }

    // Показ диалога для выбора записи на удаление
    private fun showDeleteDialog() {
        val entries = moodEntries.sortedByDescending { it.datetime }
        val items = entries.map { "${it.datetime}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите запись для удаления")
            .setItems(items) { _, which ->
                val selected = entries[which]
                AlertDialog.Builder(this)
                    .setTitle("Подтверждение")
                    .setMessage("Удалить запись от ${selected.datetime}?")
                    .setPositiveButton("Да") { _, _ ->
                        moodEntries.removeAll { it.datetime == selected.datetime }
                        saveMoods()
                        refreshMoodDisplay()
                        updateChart()
                        Toast.makeText(this, "Запись удалена", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Нет", null)
                    .show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ==================== ИМТ КАЛЬКУЛЯТОР ====================

    // Настройка секции ИМТ
    private fun setupBMISection() {
        findViewById<Button>(R.id.calculateBtn).setOnClickListener {
            val w = findViewById<EditText>(R.id.weightInput).text.toString().toDoubleOrNull()
            val h = findViewById<EditText>(R.id.heightInput).text.toString().toDoubleOrNull()
            val result = findViewById<TextView>(R.id.bmiResult)

            // Проверяем, что введены корректные значения
            if (w != null && h != null && h > 0) {
                val bmi = w / ((h/100) * (h/100))  // Формула ИМТ
                // Интерпретация результата
                result.text = when {
                    bmi < 18.5 -> "📊 ИМТ: %.1f\n⚠️ Недостаток веса".format(bmi)
                    bmi < 25 -> "📊 ИМТ: %.1f\n✅ Нормальный вес".format(bmi)
                    bmi < 30 -> "📊 ИМТ: %.1f\n⚠️ Избыточный вес".format(bmi)
                    else -> "📊 ИМТ: %.1f\n❌ Ожирение".format(bmi)
                }
            } else result.text = " Введите рост и вес"
        }
    }

    // ==================== УВЕДОМЛЕНИЯ ====================

    // Планирование уведомления о приёме лекарства
    private fun scheduleNotification(name: String, dosage: String, time: String) {
        val parts = time.split(":")
        if (parts.size < 2) return

        val now = Calendar.getInstance()
        val alarm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
        }

        // Вычисляем задержку: если время уже прошло сегодня - на завтра
        var delay = alarm.timeInMillis - now.timeInMillis
        if (delay < 0) delay += 24 * 60 * 60 * 1000

        // Создаём задачу для WorkManager
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("name" to "$name ($dosage)"))
            .build())
    }

    // ==================== DATA CLASSES (МОДЕЛИ ДАННЫХ) ====================

    // Класс для хранения информации о лекарстве
    data class Medicine(
        val name: String,      // Название лекарства
        val dosage: String,    // Дозировка
        val time: String,      // Время приёма
        val isTaken: Boolean   // Принято ли уже
    )

    // Класс для хранения записи настроения
    data class MoodEntry(
        val datetime: String,   // Дата и время записи (yyyy-MM-dd HH:mm)
        val mood: Int,          // Оценка настроения (1-5)
        val symptoms: String,   // Симптомы (через запятую)
        val trigger: String,    // Триггер (что вызвало эмоцию)
        val coping: String      // Копинг-стратегия (что помогло)
    )
}

// ==================== REMINDER WORKER (ФОНОВАЯ ЗАДАЧА ДЛЯ УВЕДОМЛЕНИЙ) ====================

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    // doWork() - выполняется в фоне в нужное время
    override fun doWork(): Result {
        // Получаем название лекарства из параметров
        val name = inputData.getString("name") ?: return Result.failure()

        // Получаем менеджер уведомлений
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Для Android 8+ создаём канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("med_channel", "Лекарства", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Напоминания о приёме лекарств"
            manager.createNotificationChannel(channel)
        }

        // Строим и отправляем уведомление
        manager.notify(name.hashCode(), androidx.core.app.NotificationCompat.Builder(applicationContext, "med_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(" Время принять лекарство!")
            .setContentText(name)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build())

        return Result.success()  // Задача выполнена успешно
    }
}