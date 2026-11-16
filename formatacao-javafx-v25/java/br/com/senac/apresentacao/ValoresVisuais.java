package br.com.senac.apresentacao;

import java.math.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.*;

/** Formatação de apresentação. Nunca altera o objeto, o saldo ou a regra contábil. */
public final class ValoresVisuais {
    private static final Locale BR=Locale.forLanguageTag("pt-BR");
    private static final Pattern ISO=Pattern.compile("(?<![\\w/-])(\\d{4}-\\d{2}-\\d{2})(?:T(\\d{2}:\\d{2})(?::\\d{2}(?:\\.\\d+)?)?)?(?![\\w/-])");
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private ValoresVisuais(){}
    public static String apresentar(Object valor,String coluna) {
        return apresentar(valor,coluna,new IdentityHashMap<>(),0);
    }
    private static String apresentar(Object valor,String coluna,IdentityHashMap<Object,Boolean> vistos,int nivel) {
        if(valor==null)return "Não informado";
        if(valor instanceof Boolean b)return b?"Sim":"Não";
        if(valor instanceof LocalDate d)return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if(valor instanceof LocalDateTime d)return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        if(valor instanceof java.sql.Date d)return apresentar(d.toLocalDate(),coluna);
        if(valor instanceof java.sql.Timestamp d)return apresentar(d.toLocalDateTime(),coluna);
        if(valor instanceof java.util.Date d)return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(d);
        if(valor instanceof Number n)return numero(n,coluna);
        if(valor instanceof Enum<?> e)return titulo(e.name());
        if(valor instanceof CharSequence c)return texto(c.toString(),coluna);
        if(valor instanceof Map<?,?> m) {
            if(m.isEmpty())return "Nenhum dado encontrado.";
            if(nivel>5||vistos.containsKey(valor))return "Referência relacionada";
            vistos.put(valor,true);
            try {
                List<String> linhas=new ArrayList<>();
                m.forEach((k,v)->{String chave=String.valueOf(k);
                    if(!chave.toLowerCase(BR).matches(".*(senha|password|secret|token|api.?key|credential).*"))
                        linhas.add(titulo(chave)+": "+apresentar(v,chave,vistos,nivel+1));});
                return String.join("\n",linhas);
            }finally{vistos.remove(valor);}
        }
        if(valor instanceof Collection<?> c) {
            if(c.isEmpty())return "Nenhum registro encontrado.";
            if(nivel>5||vistos.containsKey(valor))return "Referência relacionada";
            vistos.put(valor,true);
            try {
                List<String> linhas=new ArrayList<>();int i=0;
                for(Object item:c)linhas.add("Registro "+(++i)+"\n"+apresentar(item,coluna,vistos,nivel+1));
                return String.join("\n\n",linhas);
            }finally{vistos.remove(valor);}
        }
        // Identidade legível, em vez do toString técnico de uma entidade.
        for(String getter:List.of("getNome","getSigla","getDescricao"))try {
            Object v=valor.getClass().getMethod(getter).invoke(valor);
            if(v instanceof String s&&!s.isBlank())return s;
        }catch(ReflectiveOperationException|RuntimeException ignored){}
        if(valor.getClass().isRecord()) {
            List<String> partes=new ArrayList<>();
            for(var c:valor.getClass().getRecordComponents())try {
                Object v=c.getAccessor().invoke(valor);
                if(v instanceof Number||v instanceof CharSequence||v instanceof Boolean||v instanceof LocalDate)
                    partes.add(titulo(c.getName())+": "+apresentar(v,c.getName()));
            }catch(ReflectiveOperationException ignored){}
            if(!partes.isEmpty())return String.join(" · ",partes);
        }
        return "Registro";
    }
    public static String texto(String s,String contexto) {
        if(s==null)return "";
        String t=s.trim();
        if((t.startsWith("{")&&t.endsWith("}"))||(t.startsWith("[")&&t.endsWith("]")))try {
            Object estrutura=JSON.readValue(t,Object.class);
            if(estrutura instanceof Map<?,?>||estrutura instanceof Collection<?>)return apresentar(estrutura,contexto);
        }catch(Exception ignored){}
        if(t.equals("null"))return "Não informado";
        if(t.equals("true"))return "Sim";
        if(t.equals("false"))return "Não";
        // Strings numéricas sem contexto permanecem intactas (códigos, documentos, CEP).
        if(t.matches("-?\\d+(?:\\.\\d+)?")&&tipo(contexto)!=0)try{return numero(new BigDecimal(t),contexto);}catch(NumberFormatException ignored){}
        Matcher m=ISO.matcher(s);StringBuffer b=new StringBuffer();
        while(m.find())try {
            String d=LocalDate.parse(m.group(1)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            m.appendReplacement(b,Matcher.quoteReplacement(d+(m.group(2)==null?"":" "+m.group(2))));
        }catch(java.time.DateTimeException e){m.appendReplacement(b,Matcher.quoteReplacement(m.group()));}
        m.appendTail(b);return b.toString();
    }
    static int tipo(String contexto) {
        String s=contexto==null?"":contexto.toLowerCase(BR);
        if(s.matches(".*(percent|margem|juros|taxa|%|índice|indice).*"))return 3;
        if(s.trim().equals("total")||s.matches(".*(valor|preço|preco|custo|cmv|receita|lucro|faturamento|troco|saldo.*r\\$).*"))return 1;
        if(s.matches(".*(quantidade|qtd|saldo|peso|altura|largura|comprimento|chuva|temperatura|temp \\(|nível|nivel|distância|distancia).*"))return 2;
        return 0;
    }
    private static String numero(Number n,String contexto) {
        int tipo=tipo(contexto);
        if(tipo==0)return n.toString();
        NumberFormat f=tipo==1?NumberFormat.getCurrencyInstance(BR):NumberFormat.getNumberInstance(BR);
        f.setRoundingMode(RoundingMode.HALF_EVEN);
        if(tipo!=1){f.setMaximumFractionDigits(tipo==3?2:6);f.setMinimumFractionDigits(0);}
        String v=f.format(n);return tipo==3?v+"%":v;
    }
    private static String titulo(String s) {
        String v=s.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ').toLowerCase(BR);
        return v.isEmpty()?"":Character.toUpperCase(v.charAt(0))+v.substring(1);
    }
}
