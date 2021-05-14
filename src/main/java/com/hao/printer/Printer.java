package com.hao.printer;

import com.hao.server.util.ServerTimeUtil;

public class Printer
{
    public static Printer instance = new Printer();
    private static boolean isUIModel;

    public static void init(final boolean isUIModel) {
        if (isUIModel) {
            try {
				Printer.isUIModel = isUIModel;
			} catch (Exception e) {
				System.out.println("Error: unable to output information in UI mode, switch to command mode automatically. Details: "+e);
			}
        }
    }
    
    public void print(final String context) {
        if (Printer.instance != null) {
            if (Printer.isUIModel) {
            }
            else {
                System.out.println("[" + new String(ServerTimeUtil.accurateToSecond().getBytes()) + "]" + new String(context.getBytes()));
            }
        }
    }
}
