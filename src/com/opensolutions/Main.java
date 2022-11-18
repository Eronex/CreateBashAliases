package com.opensolutions;

import org.zeroturnaround.exec.ProcessExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
  public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
    int countAggregate = 7; // Если более 7-х раз встречается команда, создать для неё псевдоним.
    // Получение аргумента из командной строки.
    if (args.length == 1) {
      // Проверка, что передан числовой параметр.
      if (args[0].matches("^[0-9]+$")) countAggregate = Integer.parseInt(args[0]);
      System.out.println("Получен параметр: " + args[0]);
      System.out.println("Значение счётчика теперь = " + countAggregate);
    }
    TreeMap<Integer, TreeSet<String>> commandsGroupedByCounter = new TreeMap<>(Collections.reverseOrder());
    Map<String, Integer>              commandsCounter          = new HashMap<>();
    Map<String, String>               aliases                  = new HashMap<>();
    TreeMap<String, String>           aliasesPrepared          = new TreeMap<>();
    LinkedList<String>                commandsForAliases       = new LinkedList<>();
    List<String>                      sBashHistory;
    List<String>                      sBashAliases;
    String                            pathToBashHistory;
    String                            pathToBashAliases;
    ////////////////
    pathToBashHistory = System.getProperty("user.home") + System.getProperty("file.separator") + ".bash_history";
    pathToBashAliases = System.getProperty("user.home") + System.getProperty("file.separator") + ".bash_aliases";
    // Прочитать строки из файлов истории команд и из локального списка псевдонимов в массивы строк.
    sBashHistory = Files.readAllLines(Paths.get(pathToBashHistory));
    sBashAliases = Files.readAllLines(Paths.get(pathToBashAliases));
    // Перебрать уже существующие псевдонимы, чтобы совместить с будущими.
    sBashAliases.forEach(alias -> {
      String  aliasLeftPart;
      String  aliasRightPart;
      Pattern ptrnAliasLeft  = Pattern.compile("\\s\\w+=");
      Pattern ptrnAliasRight = Pattern.compile("([\"'])(?:(?=(\\\\?))\\2.)*?\\1");
      Matcher mtchAliasLeft  = ptrnAliasLeft.matcher(alias);
      Matcher mtchAliasRight = ptrnAliasRight.matcher(alias);
      while (mtchAliasLeft.find()) {
        aliasLeftPart = mtchAliasLeft.group().trim().replace("=", "");
        while (mtchAliasRight.find()) {
          aliasRightPart = mtchAliasRight.group().replace("'", "");
          aliases.put(aliasLeftPart, aliasRightPart);
        }
      }
    });
    // Перебор команд, извлечённых из файла истории команд.
    sBashHistory.forEach(command -> {
      /*
      Если команда начинается с точки,
      значит не брать такую команду в расчёт.
      */
      boolean ifNotStartWithDot = !command.startsWith(".");
      // boolean ifNotOneWord      = !command.trim().contains(" ");
      if (ifNotStartWithDot/* && ifNotOneWord*/) {
        // Заменить кое-что в команде.
        command = command.replace("'", "'\\''");
        String[] commandParts = command.split(" ");
        // Преобразование сокращённых команд в полные, если у них есть алиас.
        for (int i = 0; i < commandParts.length; i++) {
          if (aliases.containsKey(commandParts[i])) commandParts[i] = aliases.get(commandParts[i]);
        }
        for (int i = 0; i < commandParts.length; i++) {
          String commandForAliasProbably = "";
          for (int j = 0; j <= i; j++) {
            commandForAliasProbably += commandParts[j] + " ";
          }
          commandForAliasProbably = commandForAliasProbably.trim();
          // Сформировать счётчик команд, в котором будет подсчитано количество вызовов для каждой команды.
          if (commandsCounter.containsKey(commandForAliasProbably)) {
            commandsCounter.put(commandForAliasProbably, commandsCounter.get(commandForAliasProbably) + 1);
          } else commandsCounter.put(commandForAliasProbably, 1);
        }
      }
    });
    // Перебрать счётчик комадн с целью заполнения сортированного списка команд по количеству вызовов.
    for (Map.Entry<String, Integer> e : commandsCounter.entrySet()) {
      String  command = e.getKey();
      Integer count   = e.getValue();
      if (count >= countAggregate) {
        if (commandsGroupedByCounter.containsKey(count)) {
          commandsGroupedByCounter.get(count).add(command);
        } else {
          TreeSet<String> commands = new TreeSet<>();
          commands.add(command);
          commandsGroupedByCounter.put(count, commands);
        }
      }
    }
    commandsGroupedByCounter.forEach((count, commands) -> {
      HashSet<String> commandsToBeDeleted = new HashSet<>();
      commands.forEach(command -> {
        commands.forEach(command2 -> {
          if (command2.contains(command) && (!command2.equals(command))) {
            commandsToBeDeleted.add(command);
          }
        });
      });
      commands.removeAll(commandsToBeDeleted);
    });
    System.out.println("Команды-кандидаты на получение псевдонима.");
    for (Map.Entry<Integer, TreeSet<String>> entry : commandsGroupedByCounter.entrySet()) {
      Integer         count    = entry.getKey();
      TreeSet<String> commands = entry.getValue();
      System.out.println("count = " + count);
      if (true /*count >= countAggregate*/) {
        commands.forEach(command -> {
          // Вывести команду на экран и значение счётчика её повторений.
          System.out.println("\tcommand = " + command);
          // Добавить команду в список для создания псевдонимов.
          if (command.length() >= 4) commandsForAliases.add(command);
        });
      }
    }
    // Подготовить псевдонимы для команд.
    for (String command : commandsForAliases) {
      String   prepareStep1;
      String[] prepareStep2;
      int      maxLen = 0;
      // Подготовка псевдонима в несколько шагов.
      // Оставить в команде только латинские буквы и цифры и далить двойные пробелы.
      prepareStep1 = command.replaceAll("[^A-Za-z0-9]", " ").replaceAll("\\s+", " ");
      // Разбить команду на слова из-за каждого слова забирать начальные символы последовательно,
      // пока не получится псевдоним, не конфликтующий с системными командами.
      prepareStep2 = prepareStep1.split(" ");
      // Найти длину самого длинного слова.
      for (int i = 0; i < prepareStep2.length; i++) {
        if (prepareStep2[i].length() > maxLen) maxLen = prepareStep2[i].length();
      }
      // Сначала первые буквы слов взять, потом вторые, и так далее до создания
      // не конфликтующего псевдонима.
      for (int i = 0; i < maxLen; i++) {
        String       whereisOutput;
        StringBuffer prepareStep3 = new StringBuffer(); // Псевдоним.
        // Перебрать все слова из команды полученные.
        for (int j = 0; j < prepareStep2.length; j++) {
          // Взять слово, если его длина менее текущего значения порядка буквы.
          int k = prepareStep2[j].length();
          if (k > i) prepareStep3.append(prepareStep2[j].substring(0, i + 1));
          else prepareStep3.append(prepareStep2[j].substring(0, k));
        }
        // Проверить, является ли псевдоним эквивалентом команде и добавлен ли уже для этой команды псевдоним.
        boolean ifComAliEqual          = command.equals(prepareStep3.toString());
        boolean ifAliasesContainsKey   = aliasesPrepared.containsKey(prepareStep3.toString());
        boolean ifAliasesContainsValue = aliasesPrepared.containsValue(command);
        // Эти проверки нужно сделать до того, как псевдоним будет проверяться на системную команду,
        // иначе каждый вариант псевдонима вызывает системную утилиту "whereis".
        if (!ifAliasesContainsKey && !ifAliasesContainsValue && !ifComAliEqual) {
          // Выделить самый короткий псевдоним, не являющийся системной командой.
          whereisOutput = new ProcessExecutor().command("whereis", prepareStep3.toString()).readOutput(true).execute().outputUTF8();
          // Если вывод "whereis" заканчивается на двоеточие, то проверку псевдоним прошёл.
          if (whereisOutput.trim().endsWith(":")) {
            aliasesPrepared.put(prepareStep3.toString(), command);
          }
        }
      }
    }
    System.out.println();
    System.out.println(
        "Следующие псевдонимы можете добавить в файл \".bash_aliases\", находящийся в вашем домашнем каталоге. Либо создать его, если " + "отсутствует.");
    aliasesPrepared.forEach((aliasPrepared, command) -> System.out.println("alias " + aliasPrepared + "='" + command + "'"));
  }
}
