import java.util.Arrays;

import tool.sheet.SheetTool;
import tool.urlChecker.UrlChecker;
import tool.whiteLabel.WhiteLabelTool;

public class MainSelector {
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("請指定要執行的工具，例如: java MainSelector A");
			return;
		}
		
		String option = args[0];
		
		switch (option.toUpperCase()) {
			case "A":
				if (args.length < 2) {
					System.out.println("MainSelector A, 需有第二個參數指定檔案");
					System.out.println("使用方式: java MainSelector A <configFilePath> [envValuesFilePath]");
					return;
				}
				// 第三個參數可覆寫環境值設定檔路徑，預設為 ./config/env-values.json
				WhiteLabelTool.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "B":
				UrlChecker.main(new String[]{});
				break;
			case "D":
				// 子指令與選項全部往下傳，SheetTool 自己解析（無參數時會印 usage）
				SheetTool.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			default:
				System.out.println("未知選項: " + option);
				System.out.println("使用方式: java MainSelector [A|B|D]");
				break;
		}
	}
}