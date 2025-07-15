package test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BigDecimalBenchmark {
	
	// 資料結構
	static class Data {
		BigDecimal price;
		BigDecimal newPrice;
		BigDecimal rate;
		
		Data(BigDecimal price, BigDecimal newPrice, BigDecimal rate) {
			this.price = price;
			this.newPrice = newPrice;
			this.rate = rate;
		}
	}
	
	// 產生模擬資料
	static List<Data> generateData(int count) {
		Random rand = new Random();
		List<Data> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BigDecimal price = BigDecimal.valueOf(rand.nextInt(10000) + 1).divide(BigDecimal.valueOf(100));
			BigDecimal newPrice = BigDecimal.valueOf(rand.nextInt(10000) + 1).divide(BigDecimal.valueOf(100));
			BigDecimal rate = BigDecimal.valueOf(rand.nextInt(100) + 1).divide(BigDecimal.TEN);
			list.add(new Data(price, newPrice, rate));
		}
		return list;
	}
	
	// 寫法 A：直接使用 newPrice
	static void methodA(List<Data> list) {
		for (Data d : list) {
			BigDecimal C = d.newPrice.multiply(d.rate);
		}
	}
	
	// 寫法 B：根據是否等於 price 決定使用哪個乘
	static void methodB(List<Data> list) {
		int cnt = 0;
		for (Data d : list) {
			BigDecimal C = d.newPrice.multiply(d.rate);
			if (d.price.compareTo(d.newPrice) != 0) {
				C = d.newPrice.multiply(d.rate);
				cnt++;
			}
//			BigDecimal base = d.price.compareTo(d.newPrice) != 0 ? d.newPrice : d.price;
//			BigDecimal C = base.multiply(d.rate);
		}
		System.out.println(cnt);
	}
	
	public static void main(String[] args) {
		int count = 100_000;
		List<Data> data = generateData(count);
		
		long startA = System.nanoTime();
		methodA(data);
		long endA = System.nanoTime();
		System.out.printf("Method A took %.3f ms%n", (endA - startA) / 1_000_000.0);
		
		long startB = System.nanoTime();
		methodB(data);
		long endB = System.nanoTime();
		System.out.printf("Method B took %.3f ms%n", (endB - startB) / 1_000_000.0);
	}
}
