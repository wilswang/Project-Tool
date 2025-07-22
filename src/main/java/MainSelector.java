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
				if (args.length != 2) {
					System.out.println("MainSelector A, 需有第二個參數指定檔案");
					return;
				}
				WhiteLabelTool.main(new String[]{args[1]}); // 可傳遞額外參數
				break;
			case "B":
				UrlChecker.main(new String[]{});
				break;
			default:
				System.out.println("未知選項: " + option);
				System.out.println("使用方式: java MainSelector [A|B]");
				break;
		}
	}
}