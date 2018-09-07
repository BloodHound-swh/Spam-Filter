package Filter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class myFilter {
	public static void main(String[] args) {
		String[] allWord = getWords("TrainingSet/SMSSpamCollection");// 得到所有的单词

		Map<String, KeywordCount> keywordMap = new HashMap<String, KeywordCount>();// 将关键词放入HashMap中
		for (String s : allWord) {
			keywordMap.put(s, new KeywordCount(s, 0, 0, 0, 0));
		}

		List<String> mailList = getContents("TrainingSet/SMSSpamCollection");// 得到所有训练邮件列表

		int spamNumber = 0;// 统计垃圾邮件出现的次数
		int hamNumber = 0;// 统计正常邮件出现的次数

		// 统计每个关键词在垃圾邮件和正常邮件中出现的次数
		for (int i = 0; i < mailList.size(); i++) {
			String mailContent = mailList.get(i);

			// 看真实情况是否是垃圾邮件,并计数
			if (mailContent.startsWith("spam")) {
				for (Map.Entry<String, KeywordCount> entry : keywordMap.entrySet()) {
					boolean Flag = IncludeKeyword(mailContent, entry.getKey());
					KeywordCount keywordCount = entry.getValue();
					if (Flag == true) {
						keywordCount.inspam += 1;
					}
					keywordCount.spamAll += 1;// 该关键词参与到是否包含在垃圾邮件的计数，所以相当于计算了垃圾邮件的个数
				}
				spamNumber++;
			}
			// 看真实情况是否是正常邮件，并计数
			else {
				for (Map.Entry<String, KeywordCount> entry : keywordMap.entrySet()) {
					boolean Flag = IncludeKeyword(mailContent, entry.getKey());
					KeywordCount keywordCount = entry.getValue();

					if (Flag == true) {
						keywordCount.inham += 1;
					}
					keywordCount.hamAll += 1;
				}
				hamNumber++;
			}
		}

		List<String> needRemovedKey = new ArrayList<String>();
		// 计算关键词出现时是垃圾邮件的概率，判定是否保留该关键词
		for (Map.Entry<String, KeywordCount> entry : keywordMap.entrySet()) {
			KeywordCount tempKey = entry.getValue();
			if (tempKey.inspam + tempKey.inham == 0) {
				needRemovedKey.add(entry.getKey());
				continue;
			}

			double inspamP = 1.0 * tempKey.inspam / tempKey.spamAll;// 关键词在垃圾邮件中出现的概率
			double spamP = 1.0 * tempKey.spamAll / (tempKey.spamAll + tempKey.hamAll);// 垃圾邮件的先验概率
			double inhamP = 1.0 * tempKey.inham / tempKey.hamAll;// 关键词在正常邮件中出现的概率
			double hamP = 1.0 * tempKey.hamAll / (tempKey.spamAll + tempKey.hamAll);// 正常邮件的先验概率

			tempKey.combiningProbabilities = (inspamP * spamP) / (inspamP * spamP + inhamP * hamP);// 计算出现改关键词时为垃圾邮件的概率
			// 判定该关键词是否与垃圾邮件出现与否有关，低于某一阈值则丢弃
			if (tempKey.combiningProbabilities < 0.95) {
				needRemovedKey.add(entry.getKey());
			}
		}

		// 去除那些与判定垃圾邮件与否无关的关键词
		for (String s : needRemovedKey) {
			keywordMap.remove(s);
		}

		// 查看训练集结果
//		for (Map.Entry<String, KeywordCount> entry : keywordMap.entrySet()) {
//			System.out.println(entry.getValue());
//		}

		List<String> testMailList = getContents("TestSet/TestFile.txt");// 得到所有测试集邮件列表
		int rightCount = 0;
		int wrongCount = 0;
		int spamCount = 0;

		for (String mail : testMailList) {
			String thisMail = mail;
			// 测试集中垃圾邮件的数量
			if (thisMail.startsWith("spam")) {
				spamCount++;
			}

			List<String> thisMailWordList = new ArrayList<String>();
			for (Map.Entry<String, KeywordCount> entry : keywordMap.entrySet()) {
				boolean Flag = IncludeKeyword(thisMail, entry.getKey());
				if (Flag == true) {
					thisMailWordList.add(entry.getKey());
				}
			}

			// 得到这封邮件所有关键词的联合概率
			double Pup = 1.0 * spamNumber / (spamNumber + hamNumber);// 先将训练集的先验概率作为测试集的先验概率
			double Pdown = 1.0f;

			for (String kw : thisMailWordList) {
				Pup = Pup * keywordMap.get(kw).inspam / keywordMap.get(kw).spamAll;
				Pdown = Pdown * (keywordMap.get(kw).inspam + keywordMap.get(kw).inham) / (spamNumber + hamNumber);
			}

			double isSpam = Pup / (Pup + Pdown);// 联合概率公式

			System.out.println("这份邮件是垃圾邮件的概率是: " + isSpam + ", 实际是否为垃圾邮件: " + thisMail.startsWith("spam"));

			// 成功识别
			if (isSpam > 0.995 && thisMail.startsWith("spam")) {
				rightCount++;
			}
			// 识别错误
			if (isSpam > 0.995 && thisMail.startsWith("ham")) {
				wrongCount++;
			}
		}
		System.out.println("垃圾邮件总数为: " + spamCount + ", 正确识别了" + rightCount + "封垃圾邮件,召回率" + rightCount * 1.0 / spamCount
				+ ", 准确率: " + rightCount * 1.0 / (rightCount + wrongCount));
	}

	public static String[] getWords(String fileName) {
		StringBuffer sb = new StringBuffer();
		try {
			File file = new File(fileName);

			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
			String str;
			while ((str = br.readLine()) != null) {
				sb.append(str.replaceAll("spam", "").replaceAll("ham", "").trim());
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String[] a = sb.toString().split("[^a-zA-Z]+");
		return a;
	}

	public static List<String> getContents(String fileName) {
		List<String> totalList = new ArrayList<String>();
		try {
			File file = new File(fileName);

			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
			String str;
			while ((str = br.readLine()) != null) {
				totalList.add(str.trim());
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return totalList;
	}

	// 查看邮件中是否含有关键词，若包含至少一个，则返回true
	private static boolean IncludeKeyword(String strContent, String strKeyword) {
		boolean Flag = false;
		if (strContent.toLowerCase().indexOf(strKeyword.toLowerCase()) > 0) {
			Flag = true;
		}
		return Flag;
	}

}

class KeywordCount {
	public String keyword;// 关键词
	public int spamAll;// 垃圾邮件的总数
	public int inspam;// 此关键词在垃圾邮件中出现的次数
	public int hamAll;// 正常邮件的总数
	public int inham;// 此关键词在正常邮件中出现的次数
	public double combiningProbabilities;

	public KeywordCount(String keyword, int spamAll, int inspam, int hamAll, int inham) {
		super();
		this.keyword = keyword;
		this.spamAll = spamAll;
		this.inspam = inspam;
		this.hamAll = hamAll;
		this.inham = inham;
	}

	public String toString() {
		return "[关键词=" + keyword + ", 在垃圾邮件中出现的次数=" + inspam + ", 垃圾邮件的总数=" + spamAll + ", 在正常邮件中出现的次数=" + inham
				+ ", 正常邮件的总数=" + hamAll + ", 联合概率=" + combiningProbabilities + "]";
	}
}
