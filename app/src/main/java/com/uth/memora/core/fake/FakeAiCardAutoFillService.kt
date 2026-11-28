package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.model.PartOfSpeech

/**
 * Dịch vụ gợi ý tự động điền thẻ học dự phòng (In-Memory Oxford 3000 & IELTS Dictionary + Smart Phonetic/Linguistic Fallback).
 * Cung cấp phiên âm IPA chuẩn, loại từ, nghĩa tiếng Việt tự nhiên và câu ví dụ thực tế cho hàng trăm từ
 * tiếng Anh thông dụng khi thiết bị ngoại tuyến hoặc chưa kết nối được Gemini API.
 */
class FakeAiCardAutoFillService : AiCardAutoFillService {

    override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Từ vựng không được để trống."))
        }

        val normalizedKey = trimmed.lowercase()
        val exactMatch = BUILTIN_DICTIONARY[normalizedKey]
        if (exactMatch != null) {
            return Result.success(exactMatch.copy(word = trimmed))
        }

        // Thử khớp dạng nguyên mẫu nếu người dùng nhập số nhiều (-s/-es) hoặc quá khứ (-ed) / V-ing (-ing)
        val stemCandidates = listOfNotNull(
            normalizedKey.removeSuffix("s").takeIf { normalizedKey.endsWith("s") && it.length > 2 },
            normalizedKey.removeSuffix("es").takeIf { normalizedKey.endsWith("es") && it.length > 2 },
            normalizedKey.removeSuffix("ed").takeIf { normalizedKey.endsWith("ed") && it.length > 2 },
            normalizedKey.removeSuffix("ing").takeIf { normalizedKey.endsWith("ing") && it.length > 3 }
        )
        for (stem in stemCandidates) {
            val stemMatch = BUILTIN_DICTIONARY[stem]
            if (stemMatch != null) {
                return Result.success(stemMatch.copy(word = trimmed))
            }
        }

        val inferredPos = inferPartOfSpeech(normalizedKey)
        val inferredIpa = approximateIpa(normalizedKey)
        val inferredMeaning = inferMeaningVi(trimmed, inferredPos)
        val inferredExample = inferExampleSentence(trimmed, inferredPos)

        return Result.success(
            AutoFillCardData(
                word = trimmed,
                phonetic = inferredIpa,
                partOfSpeech = inferredPos,
                meaning = inferredMeaning,
                example = inferredExample
            )
        )
    }

    private fun inferPartOfSpeech(lowerWord: String): PartOfSpeech {
        return when {
            lowerWord.contains(" ") -> PartOfSpeech.OTHER
            lowerWord.endsWith("ly") && lowerWord.length > 4 -> PartOfSpeech.ADVERB
            lowerWord.endsWith("tion") || lowerWord.endsWith("sion") ||
                lowerWord.endsWith("ment") || lowerWord.endsWith("ness") ||
                lowerWord.endsWith("ity") || lowerWord.endsWith("ance") ||
                lowerWord.endsWith("ence") || lowerWord.endsWith("ship") ||
                lowerWord.endsWith("er") || lowerWord.endsWith("or") ||
                lowerWord.endsWith("ist") || lowerWord.endsWith("ism") -> PartOfSpeech.NOUN
            lowerWord.endsWith("able") || lowerWord.endsWith("ible") ||
                lowerWord.endsWith("ous") || lowerWord.endsWith("ful") ||
                lowerWord.endsWith("less") || lowerWord.endsWith("ive") ||
                lowerWord.endsWith("ic") || lowerWord.endsWith("al") ||
                lowerWord.endsWith("ent") || lowerWord.endsWith("ant") ||
                lowerWord.endsWith("ary") || lowerWord.endsWith("ory") -> PartOfSpeech.ADJECTIVE
            lowerWord.endsWith("ate") || lowerWord.endsWith("ify") ||
                lowerWord.endsWith("ize") || lowerWord.endsWith("ise") ||
                lowerWord.endsWith("en") -> PartOfSpeech.VERB
            else -> PartOfSpeech.NOUN
        }
    }

    private fun approximateIpa(lowerWord: String): String {
        val converted = lowerWord
            .replace("tion", "ʃn")
            .replace("sion", "ʒn")
            .replace("ture", "tʃə")
            .replace("ph", "f")
            .replace("sh", "ʃ")
            .replace("ch", "tʃ")
            .replace("th", "θ")
            .replace("qu", "kw")
            .replace("ck", "k")
            .replace("ee", "iː")
            .replace("oo", "uː")
        return "/$converted/"
    }

    private fun inferMeaningVi(word: String, pos: PartOfSpeech): String {
        return when (pos) {
            PartOfSpeech.VERB -> "Hành động $word; thực hiện hoặc xử lý việc liên quan đến $word"
            PartOfSpeech.ADJECTIVE -> "Mang tính chất $word; đặc trưng hoặc liên quan đến $word"
            PartOfSpeech.ADVERB -> "Một cách $word; theo hướng hoặc mức độ $word"
            PartOfSpeech.OTHER -> "Cụm từ tiếng Anh thông dụng: $word"
            else -> "Sự vật, khái niệm hoặc chủ đề liên quan đến $word"
        }
    }

    private fun inferExampleSentence(word: String, pos: PartOfSpeech): String {
        return when (pos) {
            PartOfSpeech.VERB -> "We need to $word carefully in order to achieve the best outcome."
            PartOfSpeech.ADJECTIVE -> "Having a $word attitude makes a big difference in daily work."
            PartOfSpeech.ADVERB -> "She handled the situation $word and earned everyone's respect."
            else -> "Mastering the usage of \"$word\" will help you express ideas more clearly."
        }
    }

    companion object {
        private fun entry(
            word: String,
            phonetic: String,
            pos: PartOfSpeech,
            meaning: String,
            example: String
        ): Pair<String, AutoFillCardData> = word.lowercase() to AutoFillCardData(
            word = word,
            phonetic = phonetic,
            partOfSpeech = pos,
            meaning = meaning,
            example = example
        )

        private val BUILTIN_DICTIONARY: Map<String, AutoFillCardData> = mapOf(
            // Các từ giao tiếp & học thuật phổ biến nhất (Oxford 3000 Core)
            entry("common", "/ˈkɒmən/", PartOfSpeech.ADJECTIVE, "Phổ biến, thông thường, chung, thường gặp", "Mistakes are very common when you first learn a new language."),
            entry("study", "/ˈstʌdi/", PartOfSpeech.VERB, "Học tập, nghiên cứu; sự học tập, môn học", "She likes to study English vocabulary every morning."),
            entry("learn", "/lɜːn/", PartOfSpeech.VERB, "Học, tiếp thu kiến thức hoặc kỹ năng mới", "Children learn new languages very quickly through practice."),
            entry("work", "/wɜːk/", PartOfSpeech.VERB, "Làm việc, công việc, hoạt động hiệu quả", "Hard work and consistency lead to long-term success."),
            entry("book", "/bʊk/", PartOfSpeech.NOUN, "Quyển sách; đặt chỗ trước (vé, phòng)", "I am reading an interesting book about cognitive science."),
            entry("time", "/taɪm/", PartOfSpeech.NOUN, "Thời gian, thời điểm, lần, lượt", "Managing your time wisely makes studying much less stressful."),
            entry("school", "/skuːl/", PartOfSpeech.NOUN, "Trường học, học viện", "He walks to school with his friends every day."),
            entry("student", "/ˈstjuːdnt/", PartOfSpeech.NOUN, "Học sinh, sinh viên", "Every student in the class passed the final exam."),
            entry("teacher", "/ˈtiːtʃə(r)/", PartOfSpeech.NOUN, "Giáo viên, người hướng dẫn", "Our English teacher always encourages us to speak confidently."),
            entry("language", "/ˈlæŋɡwɪdʒ/", PartOfSpeech.NOUN, "Ngôn ngữ, tiếng nói", "English is a global language used in science and business."),
            entry("vocabulary", "/vəˈkæbjələri/", PartOfSpeech.NOUN, "Từ vựng, vốn từ", "Reviewing flashcards daily helps expand your active vocabulary."),
            entry("memory", "/ˈmeməri/", PartOfSpeech.NOUN, "Trí nhớ, ký ức, bộ nhớ", "Spaced repetition strengthens long-term memory naturally."),
            entry("practice", "/ˈpræktɪs/", PartOfSpeech.NOUN, "Sự luyện tập, thực hành thường xuyên", "Consistent practice is the key to mastering pronunciation."),
            entry("knowledge", "/ˈnɒlɪdʒ/", PartOfSpeech.NOUN, "Kiến thức, sự hiểu biết", "Reading books broadens our knowledge of the world."),
            entry("success", "/səkˈses/", PartOfSpeech.NOUN, "Sự thành công, thắng lợi", "Patience and preparation are essential for career success."),
            entry("successful", "/səkˈsesfl/", PartOfSpeech.ADJECTIVE, "Thành công, đạt kết quả tốt đẹp", "The team launched a very successful mobile application."),
            entry("goal", "/ɡəʊl/", PartOfSpeech.NOUN, "Mục tiêu, đích đến", "My main goal this semester is to reach IELTS 7.0."),
            entry("habit", "/ˈhæbɪt/", PartOfSpeech.NOUN, "Thói quen hằng ngày", "Building a daily study habit takes about three weeks."),
            entry("focus", "/ˈfəʊkəs/", PartOfSpeech.VERB, "Tập trung sự chú ý, trọng tâm", "Turn off notifications so you can focus on your study session."),
            entry("understand", "/ˌʌndəˈstænd/", PartOfSpeech.VERB, "Hiểu, nắm bắt được ý nghĩa", "Writing your own example helps you understand the word deeply."),
            entry("remember", "/rɪˈmembə(r)/", PartOfSpeech.VERB, "Ghi nhớ, nhớ lại thông tin", "I still remember the first English song I learned."),
            entry("forget", "/fəˈɡet/", PartOfSpeech.VERB, "Quên, không nhớ ra", "Without regular review, it is easy to forget new words."),
            entry("create", "/kriˈeɪt/", PartOfSpeech.VERB, "Tạo ra, sáng tạo", "You can create custom flashcard decks for each subject."),
            entry("develop", "/dɪˈveləp/", PartOfSpeech.VERB, "Phát triển, xây dựng, mở rộng", "The team worked together to develop a mobile learning app."),
            entry("development", "/dɪˈveləpmənt/", PartOfSpeech.NOUN, "Sự phát triển, quá trình xây dựng", "Software development requires teamwork and careful testing."),
            entry("technology", "/tekˈnɒlədʒi/", PartOfSpeech.NOUN, "Công nghệ, kỹ thuật hiện đại", "Modern technology makes language learning accessible anywhere."),
            entry("computer", "/kəmˈpjuːtə(r)/", PartOfSpeech.NOUN, "Máy vi tính, thiết bị điện toán", "She uses her computer to write code and research articles."),
            entry("application", "/ˌæplɪˈkeɪʃn/", PartOfSpeech.NOUN, "Ứng dụng phần mềm; sự áp dụng, đơn đăng ký", "This mobile application works smoothly even without internet."),
            entry("environment", "/ɪnˈvaɪrənmənt/", PartOfSpeech.NOUN, "Môi trường sống, hoàn cảnh xung quanh", "A quiet environment helps you concentrate better while reading."),
            entry("education", "/ˌedʒuˈkeɪʃn/", PartOfSpeech.NOUN, "Giáo dục, sự đào tạo", "Quality education opens doors to better career opportunities."),
            entry("future", "/ˈfjuːtʃə(r)/", PartOfSpeech.NOUN, "Tương lai, triển vọng phía trước", "Investing in your skills today prepares you for the future."),
            entry("important", "/ɪmˈpɔːtnt/", PartOfSpeech.ADJECTIVE, "Quan trọng, có ý nghĩa lớn", "Sleep plays an important role in memory consolidation."),
            entry("difficult", "/ˈdɪfɪkəlt/", PartOfSpeech.ADJECTIVE, "Khó khăn, phức tạp, đòi hỏi nhiều nỗ lực", "At first the grammar seemed difficult, but practice made it clear."),
            entry("easy", "/ˈiːzi/", PartOfSpeech.ADJECTIVE, "Dễ dàng, đơn giản, thoải mái", "Flashcards make it easy to review vocabulary on the bus."),
            entry("hard", "/hɑːd/", PartOfSpeech.ADJECTIVE, "Chăm chỉ; cứng; khó khăn", "She worked hard all year to win the scholarship."),
            entry("good", "/ɡʊd/", PartOfSpeech.ADJECTIVE, "Tốt, giỏi, chất lượng cao", "Getting enough rest is good for your concentration."),
            entry("great", "/ɡreɪt/", PartOfSpeech.ADJECTIVE, "Tuyệt vời, to lớn, xuất sắc", "Consistent effort produces great results over time."),
            entry("new", "/njuː/", PartOfSpeech.ADJECTIVE, "Mới, mới mẻ, hiện đại", "I learned twenty new English words today."),
            entry("old", "/əʊld/", PartOfSpeech.ADJECTIVE, "Cũ, già, lâu năm", "He met an old friend at the university library."),
            entry("big", "/bɪɡ/", PartOfSpeech.ADJECTIVE, "To lớn, quan trọng", "Moving to a new city was a big decision for her."),
            entry("small", "/smɔːl/", PartOfSpeech.ADJECTIVE, "Nhỏ bé, ít ỏi", "Small daily improvements add up to massive progress."),
            entry("high", "/haɪ/", PartOfSpeech.ADJECTIVE, "Cao, ở mức độ lớn", "She achieved a very high score on the English test."),
            entry("different", "/ˈdɪfrənt/", PartOfSpeech.ADJECTIVE, "Khác biệt, đa dạng", "People learn in different ways depending on their habits."),
            entry("available", "/əˈveɪləbl/", PartOfSpeech.ADJECTIVE, "Có sẵn, sẵn dùng, rảnh rỗi", "All your flashcards are available offline anytime."),
            entry("possible", "/ˈpɒsəbl/", PartOfSpeech.ADJECTIVE, "Có thể thực hiện được, khả thi", "Please submit your assignment as soon as possible."),
            entry("necessary", "/ˈnesəsəri/", PartOfSpeech.ADJECTIVE, "Cần thiết, thiết yếu", "Patience is necessary when mastering a foreign language."),
            entry("special", "/ˈspeʃl/", PartOfSpeech.ADJECTIVE, "Đặc biệt, riêng biệt", "They prepared a special meal to celebrate her graduation."),
            entry("popular", "/ˈpɒpjələ(r)/", PartOfSpeech.ADJECTIVE, "Phổ biến, được nhiều người yêu thích", "Coffee is a very popular drink among software engineers."),
            entry("natural", "/ˈnætʃrəl/", PartOfSpeech.ADJECTIVE, "Tự nhiên, thuộc về thiên nhiên", "Listening to native speakers helps you sound more natural."),
            entry("personal", "/ˈpɜːsənl/", PartOfSpeech.ADJECTIVE, "Cá nhân, riêng tư", "Writing personal examples makes vocabulary easier to recall."),
            entry("social", "/ˈsəʊʃl/", PartOfSpeech.ADJECTIVE, "Thuộc về xã hội, giao tiếp xã hội", "Good communication is an essential social skill."),
            entry("local", "/ˈləʊkl/", PartOfSpeech.ADJECTIVE, "Thuộc địa phương, cục bộ trên thiết bị", "Your study progress is saved safely in the local database."),
            entry("global", "/ˈɡləʊbl/", PartOfSpeech.ADJECTIVE, "Toàn cầu, trên phạm vi thế giới", "Climate change is a global issue requiring cooperation."),
            entry("modern", "/ˈmɒdn/", PartOfSpeech.ADJECTIVE, "Hiện đại, tiên tiến", "Modern smartphones can recognize speech accurately."),
            entry("traditional", "/trəˈdɪʃənl/", PartOfSpeech.ADJECTIVE, "Truyền thống, theo phong tục", "Banh Chung is a traditional Vietnamese dish during Tet."),
            entry("happy", "/ˈhæpi/", PartOfSpeech.ADJECTIVE, "Vui vẻ, hạnh phúc, hài lòng", "She felt happy after completing her seven-day study streak."),
            entry("beautiful", "/ˈbjuːtɪfl/", PartOfSpeech.ADJECTIVE, "Đẹp, tuyệt vời, hấp dẫn", "We watched a beautiful sunset over the beach."),
            entry("friend", "/frend/", PartOfSpeech.NOUN, "Người bạn, bạn bè", "Studying with a friend keeps both of you motivated."),
            entry("family", "/ˈfæməli/", PartOfSpeech.NOUN, "Gia đình, người thân", "He spends every weekend having dinner with his family."),
            entry("health", "/helθ/", PartOfSpeech.NOUN, "Sức khỏe, thể trạng", "Balanced nutrition and exercise are vital for good health."),
            entry("healthy", "/ˈhelθi/", PartOfSpeech.ADJECTIVE, "Khỏe mạnh, lành mạnh, tốt cho sức khỏe", "A healthy lifestyle gives you more energy to study."),
            entry("water", "/ˈwɔːtə(r)/", PartOfSpeech.NOUN, "Nước; tưới nước", "Remember to drink enough water while working at your desk."),
            entry("food", "/fuːd/", PartOfSpeech.NOUN, "Thức ăn, thực phẩm", "Vietnamese street food is famous all over the world."),
            entry("money", "/ˈmʌni/", PartOfSpeech.NOUN, "Tiền bạc, tài chính", "Saving a little money each month builds financial security."),
            entry("music", "/ˈmjuːzɪk/", PartOfSpeech.NOUN, "Âm nhạc, bản nhạc", "Soft instrumental music helps him relax after exams."),
            entry("movie", "/ˈmuːvi/", PartOfSpeech.NOUN, "Bộ phim điện ảnh", "Watching an English movie with subtitles improves listening."),
            entry("story", "/ˈstɔːri/", PartOfSpeech.NOUN, "Câu chuyện, cốt truyện", "The novel tells an inspiring story about perseverance."),
            entry("idea", "/aɪˈdɪə/", PartOfSpeech.NOUN, "Ý tưởng, sáng kiến, quan niệm", "That is a brilliant idea for our semester project."),
            entry("problem", "/ˈprɒbləm/", PartOfSpeech.NOUN, "Vấn đề, bài toán khó cần giải quyết", "Breaking a big problem into smaller steps makes it manageable."),
            entry("solution", "/səˈluːʃn/", PartOfSpeech.NOUN, "Giải pháp, cách giải quyết", "We found a simple solution to fix the network timeout."),
            entry("result", "/rɪˈzʌlt/", PartOfSpeech.NOUN, "Kết quả; dẫn đến kết quả", "Regular review produces a much better exam result."),
            entry("reason", "/ˈriːzn/", PartOfSpeech.NOUN, "Lý do, nguyên nhân", "Consistency is the main reason behind her rapid progress."),
            entry("change", "/tʃeɪndʒ/", PartOfSpeech.VERB, "Thay đổi, sự thay đổi", "Small changes in your daily routine can transform your life."),
            entry("help", "/help/", PartOfSpeech.VERB, "Giúp đỡ, hỗ trợ; sự trợ giúp", "Mnemonics help you connect new words with vivid images."),
            entry("use", "/juːz/", PartOfSpeech.VERB, "Sử dụng, dùng; cách sử dụng", "Try to use each new word in a complete sentence."),
            entry("make", "/meɪk/", PartOfSpeech.VERB, "Làm, tạo ra, chế tạo", "Do not be afraid to make mistakes when speaking English."),
            entry("take", "/teɪk/", PartOfSpeech.VERB, "Lấy, cầm, mất (thời gian), tham gia", "It takes about twenty minutes to complete a review session."),
            entry("give", "/ɡɪv/", PartOfSpeech.VERB, "Cho, tặng, cung cấp, đưa ra", "The app gives instant grammar feedback on your sentences."),
            entry("find", "/faɪnd/", PartOfSpeech.VERB, "Tìm thấy, phát hiện, nhận thấy", "You can easily find any card using the search bar."),
            entry("think", "/θɪŋk/", PartOfSpeech.VERB, "Suy nghĩ, cho rằng, cân nhắc", "Take a moment to think about the meaning before flipping."),
            entry("know", "/nəʊ/", PartOfSpeech.VERB, "Biết, hiểu rõ, quen biết", "Practice helps you truly know a word rather than guess it."),
            entry("speak", "/spiːk/", PartOfSpeech.VERB, "Nói, phát biểu, giao tiếp bằng lời", "She can speak both Vietnamese and English fluently."),
            entry("write", "/raɪt/", PartOfSpeech.VERB, "Viết, soạn thảo", "Please write a complete sentence containing the target word."),
            entry("read", "/riːd/", PartOfSpeech.VERB, "Đọc, nghiên cứu tài liệu", "I like to read technology news in English every morning."),
            entry("listen", "/ˈlɪsn/", PartOfSpeech.VERB, "Lắng nghe, chú ý nghe", "Tap the speaker icon to listen to the standard pronunciation."),
            entry("travel", "/ˈtrævl/", PartOfSpeech.VERB, "Đi du lịch, di chuyển tới nơi khác", "Learning English allows you to travel confidently around the world."),
            entry("live", "/lɪv/", PartOfSpeech.VERB, "Sống, sinh sống, cư trú", "We only live once, so make every day meaningful."),
            entry("life", "/laɪf/", PartOfSpeech.NOUN, "Cuộc sống, đời sống, sinh mạng", "Continuous learning enriches your personal and professional life."),
            entry("world", "/wɜːld/", PartOfSpeech.NOUN, "Thế giới, hoàn cầu", "Technology connects people from all corners of the world."),
            entry("country", "/ˈkʌntri/", PartOfSpeech.NOUN, "Đất nước, quốc gia; vùng nông thôn", "Vietnam is a beautiful country with rich cultural heritage."),
            entry("city", "/ˈsɪti/", PartOfSpeech.NOUN, "Thành phố, đô thị", "Ho Chi Minh City is a dynamic center of commerce and education."),
            entry("home", "/həʊm/", PartOfSpeech.NOUN, "Ngôi nhà, tổ ấm, trang chủ", "She reviews her flashcards on the train ride home."),
            entry("house", "/haʊs/", PartOfSpeech.NOUN, "Ngôi nhà, tòa nhà", "They planted green trees around their house."),
            entry("company", "/ˈkʌmpəni/", PartOfSpeech.NOUN, "Công ty, doanh nghiệp", "He works as a software engineer at an international company."),
            entry("business", "/ˈbɪznəs/", PartOfSpeech.NOUN, "Kinh doanh, công việc thương mại", "Clear English communication is vital in international business."),
            entry("project", "/ˈprɒdʒekt/", PartOfSpeech.NOUN, "Dự án, đồ án môn học", "Our team built the Memora flashcard app for our final project."),
            entry("team", "/tiːm/", PartOfSpeech.NOUN, "Đội, nhóm làm việc chung", "Every member of the team contributed to the project's success."),
            entry("system", "/ˈsɪstəm/", PartOfSpeech.NOUN, "Hệ thống, phương pháp tổ chức", "The spaced repetition system schedules reviews at optimal intervals."),
            entry("data", "/ˈdeɪtə/", PartOfSpeech.NOUN, "Dữ liệu, thông tin lưu trữ", "Your learning data is synchronized securely across devices."),
            entry("information", "/ˌɪnfəˈmeɪʃn/", PartOfSpeech.NOUN, "Thông tin, tài liệu", "Reviewing helps transfer information from short-term to long-term memory."),
            entry("question", "/ˈkwestʃən/", PartOfSpeech.NOUN, "Câu hỏi, vấn đề cần giải đáp", "Feel free to ask a question if any grammar point is unclear."),
            entry("answer", "/ˈɑːnsə(r)/", PartOfSpeech.NOUN, "Câu trả lời, đáp án; trả lời", "Check your answer by flipping the flashcard."),
            entry("example", "/ɪɡˈzɑːmpl/", PartOfSpeech.NOUN, "Ví dụ minh họa, gương mẫu", "A good example sentence makes a word much easier to remember."),
            entry("meaning", "/ˈmiːnɪŋ/", PartOfSpeech.NOUN, "Ý nghĩa, nghĩa của từ", "Try to recall the Vietnamese meaning before flipping the card."),
            entry("word", "/wɜːd/", PartOfSpeech.NOUN, "Từ, từ vựng, lời nói", "Learn each new word together with its pronunciation and context."),
            entry("sentence", "/ˈsentəns/", PartOfSpeech.NOUN, "Câu văn; đặt câu", "Writing your own sentence is the best way to master a word."),
            entry("hello", "/həˈləʊ/", PartOfSpeech.OTHER, "Xin chào (lời chào thông dụng)", "She smiled and said hello to her new classmates."),
            entry("welcome", "/ˈwelkəm/", PartOfSpeech.VERB, "Chào mừng, hoan nghênh, đón tiếp", "Welcome to Memora, your smart flashcard learning companion."),
            entry("thank", "/θæŋk/", PartOfSpeech.VERB, "Cảm ơn, bày tỏ lòng biết ơn", "I would like to thank my teammates for their hard work."),
            entry("love", "/lʌv/", PartOfSpeech.VERB, "Yêu thích, tình yêu thương", "Students love how fast and intuitive the flashcard app feels."),
            entry("hope", "/həʊp/", PartOfSpeech.VERB, "Hy vọng, niềm hy vọng", "We hope this app helps you achieve your language learning goals."),
            entry("believe", "/bɪˈliːv/", PartOfSpeech.VERB, "Tin tưởng, cho rằng", "I strongly believe that daily practice leads to mastery."),
            entry("achieve", "/əˈtʃiːv/", PartOfSpeech.VERB, "Đạt được, hoàn thành mục tiêu sau nhiều nỗ lực", "She worked hard to achieve her career goals."),
            entry("achievement", "/əˈtʃiːvmənt/", PartOfSpeech.NOUN, "Thành tựu, thành tích đạt được", "Graduating with honors is a remarkable personal achievement."),
            entry("resilient", "/rɪˈzɪliənt/", PartOfSpeech.ADJECTIVE, "Kiên cường, bền bỉ, nhanh phục hồi sau khó khăn", "She remained resilient despite facing many tough challenges."),
            entry("eloquent", "/ˈeləkwənt/", PartOfSpeech.ADJECTIVE, "Hùng biện, lưu loát và giàu sức thuyết phục", "The speaker delivered an eloquent presentation on climate action."),
            entry("pragmatic", "/præɡˈmætɪk/", PartOfSpeech.ADJECTIVE, "Thực tế, thực dụng, hướng tới giải pháp khả thi", "We need a pragmatic approach to finish the project on schedule."),
            entry("biodiversity", "/ˌbaɪəʊdaɪˈvɜːsəti/", PartOfSpeech.NOUN, "Sự đa dạng sinh học", "Protecting forests is essential for preserving global biodiversity."),
            entry("habitat", "/ˈhæbɪtæt/", PartOfSpeech.NOUN, "Môi trường sống tự nhiên của sinh vật", "Deforestation destroys the natural habitat of many wild species."),
            entry("narrative", "/ˈnærətɪv/", PartOfSpeech.NOUN, "Câu chuyện kể, lối trần thuật", "The film weaves a compelling narrative about friendship and courage."),

            // Oxford 3000 cơ bản (20 từ)
            entry("approach", "/əˈprəʊtʃ/", PartOfSpeech.NOUN, "Cách tiếp cận, phương pháp giải quyết vấn đề", "We need a practical approach to solve this technical issue."),
            entry("benefit", "/ˈbenɪfɪt/", PartOfSpeech.NOUN, "Lợi ích, quyền lợi mang lại giá trị tốt", "Regular exercise brings many long-term health benefits."),
            entry("challenge", "/ˈtʃælɪndʒ/", PartOfSpeech.NOUN, "Thử thách, nhiệm vụ khó đòi hỏi quyết tâm", "Learning a new language is always an exciting challenge."),
            entry("communicate", "/kəˈmjuːnɪkeɪt/", PartOfSpeech.VERB, "Giao tiếp, truyền đạt thông tin hoặc cảm xúc", "Good leaders communicate clearly with every team member."),
            entry("communication", "/kəˌmjuːnɪˈkeɪʃn/", PartOfSpeech.NOUN, "Sự giao tiếp, truyền thông, liên lạc", "Effective communication is the foundation of a strong team."),
            entry("confident", "/ˈkɒnfɪdənt/", PartOfSpeech.ADJECTIVE, "Tự tin vào khả năng của bản thân", "Daily practice makes her feel confident during English interviews."),
            entry("confidence", "/ˈkɒnfɪdəns/", PartOfSpeech.NOUN, "Sự tự tin, niềm tin tưởng", "Speaking every day builds your confidence naturally."),
            entry("consider", "/kənˈsɪdə(r)/", PartOfSpeech.VERB, "Cân nhắc, xem xét kỹ trước khi quyết định", "Please consider all available options before signing the contract."),
            entry("contribute", "/kənˈtrɪbjuːt/", PartOfSpeech.VERB, "Đóng góp công sức, ý tưởng hoặc tài chính", "Everyone is encouraged to contribute ideas during the meeting."),
            entry("convenient", "/kənˈviːniənt/", PartOfSpeech.ADJECTIVE, "Thuận tiện, tiện lợi và tiết kiệm thời gian", "Studying flashcards on a mobile phone is very convenient."),
            entry("effective", "/ɪˈfektɪv/", PartOfSpeech.ADJECTIVE, "Hiệu quả, đem lại kết quả như mong đợi", "Spaced repetition is an effective method for vocabulary retention."),
            entry("encourage", "/ɪnˈkʌrɪdʒ/", PartOfSpeech.VERB, "Khuyến khích, động viên tinh thần", "Teachers always encourage students to write their own examples."),
            entry("essential", "/ɪˈsenʃl/", PartOfSpeech.ADJECTIVE, "Thiết yếu, vô cùng quan trọng và không thể thiếu", "Clear documentation is essential for software maintenance."),
            entry("experience", "/ɪkˈspɪəriəns/", PartOfSpeech.NOUN, "Kinh nghiệm thực tế hoặc trải nghiệm đáng nhớ", "She gained valuable experience while working on the project."),
            entry("flexible", "/ˈfleksəbl/", PartOfSpeech.ADJECTIVE, "Linh hoạt, dễ thích nghi với sự thay đổi", "Our team maintains a flexible schedule during exam weeks."),
            entry("frequent", "/ˈfriːkwənt/", PartOfSpeech.ADJECTIVE, "Thường xuyên, diễn ra đều đặn nhiều lần", "Frequent short reviews work much better than cramming."),
            entry("improve", "/ɪmˈpruːv/", PartOfSpeech.VERB, "Cải thiện, nâng cao chất lượng hoặc kỹ năng", "Reading English articles daily helps improve your vocabulary."),
            entry("maintain", "/meɪnˈteɪn/", PartOfSpeech.VERB, "Duy trì, giữ gìn ở mức ổn định", "It takes discipline to maintain a thirty-day study streak."),
            entry("opportunity", "/ˌɒpəˈtjuːnəti/", PartOfSpeech.NOUN, "Cơ hội thuận lợi để phát triển", "The internship gave him an opportunity to practice real engineering."),
            entry("prepare", "/prɪˈpeə(r)/", PartOfSpeech.VERB, "Chuẩn bị sẵn sàng cho công việc hoặc sự kiện", "They spent two weeks preparing for the final presentation."),
            entry("reliable", "/rɪˈlaɪəbl/", PartOfSpeech.ADJECTIVE, "Đáng tin cậy, hoạt động ổn định và chắc chắn", "Local database storage keeps the app reliable even offline."),

            // IELTS cơ bản (20 từ)
            entry("abundant", "/əˈbʌndənt/", PartOfSpeech.ADJECTIVE, "Dồi dào, phong phú, có số lượng rất lớn", "Solar energy is an abundant natural resource in tropical regions."),
            entry("accumulate", "/əˈkjuːmjəleɪt/", PartOfSpeech.VERB, "Tích lũy, gom góp dần theo thời gian", "Learners accumulate hundreds of active words through daily review."),
            entry("adept", "/əˈdept/", PartOfSpeech.ADJECTIVE, "Tinh thông, thành thạo một kỹ năng phức tạp", "She became adept at analyzing statistical charts for IELTS Writing."),
            entry("advocate", "/ˈædvəkeɪt/", PartOfSpeech.VERB, "Ủng hộ công khai, đề xuất một chính sách hoặc giải pháp", "Many scientists advocate reducing single-use plastic products."),
            entry("ambiguous", "/æmˈbɪɡjuəs/", PartOfSpeech.ADJECTIVE, "Mơ hồ, nhập nhằng, có thể hiểu theo nhiều nghĩa", "Avoid writing ambiguous sentences in academic essays."),
            entry("coherent", "/kəʊˈhɪərənt/", PartOfSpeech.ADJECTIVE, "Mạch lạc, chặt chẽ và có tính liên kết rõ ràng", "A high-scoring essay requires a coherent structure across paragraphs."),
            entry("comprehensive", "/ˌkɒmprɪˈhensɪv/", PartOfSpeech.ADJECTIVE, "Toàn diện, bao quát đầy đủ các khía cạnh", "The report provides a comprehensive overview of urban transport."),
            entry("consequence", "/ˈkɒnsɪkwəns/", PartOfSpeech.NOUN, "Hệ quả, hậu quả trực tiếp của một hành động", "Climate change is a serious consequence of industrial pollution."),
            entry("crucial", "/ˈkruːʃl/", PartOfSpeech.ADJECTIVE, "Mang tính quyết định, then chốt", "Time management plays a crucial role during the reading test."),
            entry("diminish", "/dɪˈmɪnɪʃ/", PartOfSpeech.VERB, "Suy giảm, thu hẹp về mức độ hoặc tầm quan trọng", "Without timely review, memory retention begins to diminish rapidly."),
            entry("diverse", "/daɪˈvɜːs/", PartOfSpeech.ADJECTIVE, "Đa dạng, gồm nhiều thành phần khác biệt", "Modern universities welcome students from diverse cultural backgrounds."),
            entry("emphasize", "/ˈemfəsaɪz/", PartOfSpeech.VERB, "Nhấn mạnh tầm quan trọng của một luận điểm", "Educators emphasize active recall over passive reading."),
            entry("feasible", "/ˈfiːzəbl/", PartOfSpeech.ADJECTIVE, "Khả thi, có thể thực hiện được trong thực tế", "Building bicycle lanes is a feasible solution to traffic congestion."),
            entry("fluctuate", "/ˈflʌktʃueɪt/", PartOfSpeech.VERB, "Dao động, biến động lên xuống không cố định", "Housing prices continued to fluctuate throughout the decade."),
            entry("inevitable", "/ɪnˈevɪtəbl/", PartOfSpeech.ADJECTIVE, "Tất yếu, chắc chắn xảy ra không thể tránh khỏi", "Digital transformation has become inevitable in higher education."),
            entry("mitigate", "/ˈmɪtɪɡeɪt/", PartOfSpeech.VERB, "Giảm thiểu, làm dịu bớt tác động tiêu cực", "Planting urban forests helps mitigate the heat island effect."),
            entry("obstacle", "/ˈɒbstəkl/", PartOfSpeech.NOUN, "Rào cản, chướng ngại vật cản trở tiến trình", "Lack of vocabulary is a major obstacle to fluent speaking."),
            entry("perspective", "/pəˈspektɪv/", PartOfSpeech.NOUN, "Góc nhìn, quan điểm đánh giá vấn đề", "Studying abroad broadens your perspective on global issues."),
            entry("predominant", "/prɪˈdɒmɪnənt/", PartOfSpeech.ADJECTIVE, "Chiếm ưu thế, nổi trội nhất trong một nhóm", "Renewable power is becoming the predominant source of electricity."),
            entry("sustainable", "/səˈsteɪnəbl/", PartOfSpeech.ADJECTIVE, "Bền vững, duy trì lâu dài mà không gây cạn kiệt", "Twenty new cards per day is a sustainable study pace.")
        )
    }
}
