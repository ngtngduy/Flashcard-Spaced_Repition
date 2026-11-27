package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.model.PartOfSpeech

/**
 * Dịch vụ gợi ý tự động điền thẻ học dự phòng (In-Memory Dictionary + Smart Linguistic Fallback).
 * Cung cấp phiên âm IPA chuẩn, loại từ, nghĩa tiếng Việt tự nhiên và câu ví dụ thực tế cho các từ
 * tiếng Anh thông dụng (Oxford 3000 / IELTS / Giao tiếp hằng ngày) khi chưa cấu hình hoặc chưa kết nối được Gemini API.
 */
class FakeAiCardAutoFillService : AiCardAutoFillService {

    override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Từ vựng không được để trống."))
        }

        val normalizedKey = trimmed.lowercase()
        val match = BUILTIN_DICTIONARY[normalizedKey]
        if (match != null) {
            return Result.success(match.copy(word = trimmed))
        }

        val inferredPos = inferPartOfSpeech(normalizedKey)
        val inferredMeaning = inferMeaningVi(trimmed, inferredPos)
        val inferredExample = inferExampleSentence(trimmed, inferredPos)

        return Result.success(
            AutoFillCardData(
                word = trimmed,
                phonetic = "/${normalizedKey}/",
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
                lowerWord.endsWith("ence") || lowerWord.endsWith("ship") -> PartOfSpeech.NOUN
            lowerWord.endsWith("able") || lowerWord.endsWith("ible") ||
                lowerWord.endsWith("ous") || lowerWord.endsWith("ful") ||
                lowerWord.endsWith("less") || lowerWord.endsWith("ive") ||
                lowerWord.endsWith("ic") || lowerWord.endsWith("al") -> PartOfSpeech.ADJECTIVE
            lowerWord.endsWith("ate") || lowerWord.endsWith("ify") ||
                lowerWord.endsWith("ize") || lowerWord.endsWith("ise") -> PartOfSpeech.VERB
            else -> PartOfSpeech.NOUN
        }
    }

    private fun inferMeaningVi(word: String, pos: PartOfSpeech): String {
        return when (pos) {
            PartOfSpeech.VERB -> "Thực hiện hành động $word (động từ tiếng Anh)"
            PartOfSpeech.ADJECTIVE -> "Có tính chất, đặc điểm $word (tính từ tiếng Anh)"
            PartOfSpeech.ADVERB -> "Một cách $word (trạng từ tiếng Anh)"
            PartOfSpeech.OTHER -> "Cụm từ / thành ngữ tiếng Anh: $word"
            else -> "Từ vựng tiếng Anh: $word"
        }
    }

    private fun inferExampleSentence(word: String, pos: PartOfSpeech): String {
        return when (pos) {
            PartOfSpeech.VERB -> "We should $word carefully to achieve better results."
            PartOfSpeech.ADJECTIVE -> "Having a $word mindset is important for daily learning."
            PartOfSpeech.ADVERB -> "She completed the task $word and on time."
            else -> "Understanding the concept of $word helps improve communication."
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
            // Từ thông dụng hằng ngày
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
            entry("goal", "/ɡəʊl/", PartOfSpeech.NOUN, "Mục tiêu, đích đến", "My main goal this semester is to reach IELTS 7.0."),
            entry("habit", "/ˈhæbɪt/", PartOfSpeech.NOUN, "Thói quen hằng ngày", "Building a daily study habit takes about three weeks."),
            entry("focus", "/ˈfəʊkəs/", PartOfSpeech.VERB, "Tập trung sự chú ý, trọng tâm", "Turn off notifications so you can focus on your study session."),
            entry("understand", "/ˌʌndəˈstænd/", PartOfSpeech.VERB, "Hiểu, nắm bắt được ý nghĩa", "Writing your own example helps you understand the word deeply."),
            entry("remember", "/rɪˈmembə(r)/", PartOfSpeech.VERB, "Ghi nhớ, nhớ lại thông tin", "I still remember the first English song I learned."),
            entry("forget", "/fəˈɡet/", PartOfSpeech.VERB, "Quên, không nhớ ra", "Without regular review, it is easy to forget new words."),
            entry("create", "/kriˈeɪt/", PartOfSpeech.VERB, "Tạo ra, sáng tạo", "You can create custom flashcard decks for each subject."),
            entry("develop", "/dɪˈveləp/", PartOfSpeech.VERB, "Phát triển, xây dựng, mở rộng", "The team worked together to develop a mobile learning app."),
            entry("technology", "/tekˈnɒlədʒi/", PartOfSpeech.NOUN, "Công nghệ, kỹ thuật hiện đại", "Modern technology makes language learning accessible anywhere."),
            entry("computer", "/kəmˈpjuːtə(r)/", PartOfSpeech.NOUN, "Máy vi tính, thiết bị điện toán", "She uses her computer to write code and research articles."),
            entry("application", "/ˌæplɪˈkeɪʃn/", PartOfSpeech.NOUN, "Ứng dụng phần mềm; sự áp dụng, đơn đăng ký", "This mobile application works smoothly even without internet."),
            entry("environment", "/ɪnˈvaɪrənmənt/", PartOfSpeech.NOUN, "Môi trường sống, hoàn cảnh xung quanh", "A quiet environment helps you concentrate better while reading."),
            entry("education", "/ˌedʒuˈkeɪʃn/", PartOfSpeech.NOUN, "Giáo dục, sự đào tạo", "Quality education opens doors to better career opportunities."),
            entry("future", "/ˈfjuːtʃə(r)/", PartOfSpeech.NOUN, "Tương lai, triển vọng phía trước", "Investing in your skills today prepares you for the future."),
            entry("important", "/ɪmˈpɔːtnt/", PartOfSpeech.ADJECTIVE, "Quan trọng, có ý nghĩa lớn", "Sleep plays an important role in memory consolidation."),
            entry("difficult", "/ˈdɪfɪkəlt/", PartOfSpeech.ADJECTIVE, "Khó khăn, phức tạp, đòi hỏi nhiều nỗ lực", "At first the grammar seemed difficult, but practice made it clear."),
            entry("happy", "/ˈhæpi/", PartOfSpeech.ADJECTIVE, "Vui vẻ, hạnh phúc, hài lòng", "She felt happy after completing her seven-day study streak."),
            entry("beautiful", "/ˈbjuːtɪfl/", PartOfSpeech.ADJECTIVE, "Đẹp, tuyệt vời, hấp dẫn", "We watched a beautiful sunset over the beach."),
            entry("friend", "/frend/", PartOfSpeech.NOUN, "Người bạn, bạn bè", "Studying with a friend keeps both of you motivated."),
            entry("family", "/ˈfæməli/", PartOfSpeech.NOUN, "Gia đình, người thân", "He spends every weekend having dinner with his family."),
            entry("health", "/helθ/", PartOfSpeech.NOUN, "Sức khỏe, thể trạng", "Balanced nutrition and exercise are vital for good health."),
            entry("travel", "/ˈtrævl/", PartOfSpeech.VERB, "Đi du lịch, di chuyển tới nơi khác", "Learning English allows you to travel confidently around the world."),
            entry("resilient", "/rɪˈzɪliənt/", PartOfSpeech.ADJECTIVE, "Kiên cường, bền bỉ, nhanh phục hồi sau khó khăn", "She remained resilient despite facing many tough challenges."),
            entry("eloquent", "/ˈeləkwənt/", PartOfSpeech.ADJECTIVE, "Hùng biện, lưu loát và giàu sức thuyết phục", "The speaker delivered an eloquent presentation on climate action."),
            entry("pragmatic", "/præɡˈmætɪk/", PartOfSpeech.ADJECTIVE, "Thực tế, thực dụng, hướng tới giải pháp khả thi", "We need a pragmatic approach to finish the project on schedule."),

            // Oxford 3000 cơ bản (20 từ)
            entry("achieve", "/əˈtʃiːv/", PartOfSpeech.VERB, "Đạt được, hoàn thành mục tiêu sau nhiều nỗ lực", "She worked hard to achieve her career goals."),
            entry("approach", "/əˈprəʊtʃ/", PartOfSpeech.NOUN, "Cách tiếp cận, phương pháp giải quyết vấn đề", "We need a practical approach to solve this technical issue."),
            entry("benefit", "/ˈbenɪfɪt/", PartOfSpeech.NOUN, "Lợi ích, quyền lợi mang lại giá trị tốt", "Regular exercise brings many long-term health benefits."),
            entry("challenge", "/ˈtʃælɪndʒ/", PartOfSpeech.NOUN, "Thử thách, nhiệm vụ khó đòi hỏi quyết tâm", "Learning a new language is always an exciting challenge."),
            entry("communicate", "/kəˈmjuːnɪkeɪt/", PartOfSpeech.VERB, "Giao tiếp, truyền đạt thông tin hoặc cảm xúc", "Good leaders communicate clearly with every team member."),
            entry("confident", "/ˈkɒnfɪdənt/", PartOfSpeech.ADJECTIVE, "Tự tin vào khả năng của bản thân", "Daily practice makes her feel confident during English interviews."),
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
